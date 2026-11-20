ALTER TABLE public.account ADD COLUMN credential_revision bigint NOT NULL DEFAULT 0
    CHECK (credential_revision >= 0);
GRANT SELECT (version, credential_revision) ON public.account TO etaye_identity;

-- Linking and invitation workflows remain separate. This table represents verified grants only.
CREATE TABLE public.portal_link (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id uuid NOT NULL REFERENCES public.account(id) ON DELETE CASCADE,
    practice_id uuid NOT NULL,
    record_id uuid NOT NULL,
    verified_by uuid NOT NULL REFERENCES public.account(id),
    verified_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    state text NOT NULL CHECK (state IN ('ACTIVE', 'REVOKED')),
    version bigint NOT NULL DEFAULT 0,
    FOREIGN KEY (practice_id, record_id) REFERENCES public.patient_record(practice_id, id)
);
CREATE UNIQUE INDEX portal_link_one_active_record ON public.portal_link (record_id) WHERE state = 'ACTIVE';
CREATE INDEX portal_link_account ON public.portal_link (account_id);

ALTER TABLE public.portal_link ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.portal_link FORCE ROW LEVEL SECURITY;
CREATE POLICY portal_link_owner ON public.portal_link TO etaye_owner USING (true) WITH CHECK (true);

CREATE TABLE public.account_recovery_receipt (
    request_digest text PRIMARY KEY CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    expires_at timestamptz NOT NULL
);
CREATE INDEX account_recovery_receipt_expiry ON public.account_recovery_receipt (expires_at);

CREATE TABLE public.account_recovery_token (
    token_digest text PRIMARY KEY CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    account_id uuid NOT NULL UNIQUE REFERENCES public.account(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL
);
CREATE INDEX account_recovery_token_expiry ON public.account_recovery_token (expires_at);

CREATE TABLE public.account_access_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id uuid NOT NULL,
    event_type text NOT NULL CHECK (event_type IN ('SIGNED_IN', 'PASSWORD_RESET')),
    correlation_id varchar(200) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE FUNCTION public.find_sign_in_credential(email text)
RETURNS TABLE (account_id uuid, encoded_password text, credential_revision bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
AS $$
    SELECT a.id, a.password_hash::text, a.credential_revision
    FROM public.account a WHERE a.normalized_email = email AND a.state = 'ACTIVE'
$$;

CREATE FUNCTION public.current_session_grants(actor uuid)
RETURNS TABLE (grant_id uuid, grant_type text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
AS $$
    SELECT m.id, 'MEMBERSHIP'::text
    FROM public.membership m JOIN public.practice p ON p.id = m.practice_id AND p.state = 'ACTIVE'
    WHERE m.account_id = actor AND m.state = 'ACTIVE'
      AND actor = NULLIF(current_setting('etaye.account_id', true), '')::uuid
    UNION ALL
    SELECT link.id, 'PORTAL'::text
    FROM public.portal_link link JOIN public.practice p ON p.id = link.practice_id AND p.state = 'ACTIVE'
    WHERE link.account_id = actor AND link.state = 'ACTIVE'
      AND actor = NULLIF(current_setting('etaye.account_id', true), '')::uuid
$$;

CREATE FUNCTION public.record_account_sign_in(actor uuid, revision bigint, correlation text)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $$
BEGIN
    PERFORM 1 FROM public.account a
    WHERE a.id = actor AND a.state = 'ACTIVE' AND a.credential_revision = revision FOR UPDATE;
    IF NOT FOUND THEN
        RETURN false;
    END IF;

    INSERT INTO public.account_access_audit (account_id, event_type, correlation_id)
    VALUES (actor, 'SIGNED_IN', correlation);
    RETURN true;
END
$$;

CREATE FUNCTION public.start_account_recovery(email text, receipt text, digest text)
RETURNS timestamptz
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $$
DECLARE
    actor uuid;
    expires timestamptz;
BEGIN
    -- Serialize receipt admission so the database budget holds across application instances.
    PERFORM pg_advisory_xact_lock(51008, 2);
    expires := clock_timestamp() + interval '15 minutes';
    DELETE FROM public.account_recovery_receipt r WHERE r.expires_at <= clock_timestamp();
    DELETE FROM public.account_recovery_token t WHERE t.expires_at <= clock_timestamp();

    IF (SELECT count(*) FROM public.account_recovery_receipt) >= 5000 THEN
        RETURN NULL;
    END IF;
    INSERT INTO public.account_recovery_receipt (request_digest, expires_at) VALUES (receipt, expires)
    ON CONFLICT DO NOTHING;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT a.id INTO actor FROM public.account a
    WHERE a.normalized_email = email AND a.state = 'ACTIVE' FOR UPDATE;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    INSERT INTO public.account_recovery_token (account_id, token_digest, expires_at)
    VALUES (actor, digest, expires)
    ON CONFLICT (account_id) DO UPDATE
    SET token_digest = EXCLUDED.token_digest, expires_at = EXCLUDED.expires_at;
    RETURN expires;
END
$$;

CREATE FUNCTION public.redeem_account_recovery(digest text, encoded text, correlation text)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $$
DECLARE
    actor uuid;
BEGIN
    SELECT t.account_id INTO actor FROM public.account_recovery_token t
    WHERE t.token_digest = digest AND t.expires_at > clock_timestamp();
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    -- Match issuance's account-then-token lock order, then check expiry again after waiting.
    PERFORM 1 FROM public.account a WHERE a.id = actor AND a.state = 'ACTIVE' FOR UPDATE;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    DELETE FROM public.account_recovery_token t
    WHERE t.account_id = actor AND t.token_digest = digest AND t.expires_at > clock_timestamp();
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    UPDATE public.account a SET
        password_hash = encoded, version = a.version + 1, updated_at = clock_timestamp(),
        credential_revision = a.credential_revision + 1
    WHERE a.id = actor;
    DELETE FROM public.spring_session s WHERE s.principal_name = actor::text;
    INSERT INTO public.account_access_audit (account_id, event_type, correlation_id)
    VALUES (actor, 'PASSWORD_RESET', correlation);
    RETURN actor;
END
$$;

REVOKE ALL ON FUNCTION public.find_sign_in_credential(text),
    public.current_session_grants(uuid), public.record_account_sign_in(uuid, bigint, text),
    public.start_account_recovery(text, text, text), public.redeem_account_recovery(text, text, text)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.find_sign_in_credential(text),
    public.current_session_grants(uuid), public.record_account_sign_in(uuid, bigint, text),
    public.start_account_recovery(text, text, text), public.redeem_account_recovery(text, text, text)
    TO etaye_identity;
