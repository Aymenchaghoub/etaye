-- V3__invitation_activation.sql
-- Story 1.1: practice-owned invitations with digest-only tokens and atomic single-use acceptance.

CREATE TABLE public.invitation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    practice_id uuid NOT NULL REFERENCES public.practice(id),
    purpose text NOT NULL CHECK (purpose IN ('TEAM_MEMBERSHIP', 'PATIENT_PORTAL')),
    intended_recipient text NOT NULL,
    token_digest text NOT NULL UNIQUE CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    record_id uuid,
    capabilities text[] NOT NULL DEFAULT '{}' CHECK (
        capabilities <@ ARRAY[
            'TEAM_MANAGE', 'PRACTICE_SETTINGS', 'ADMIN_AUDIT',
            'CLINICAL_READ', 'CLINICAL_WRITE', 'SCHEDULE_MANAGE',
            'CLINICAL_VALIDATE', 'PATIENT_PUBLISH', 'CLINICAL_AUDIT',
            'PATIENT_REGISTER'
        ]::text[]
    ),
    issuer_id uuid NOT NULL REFERENCES public.account(id),
    state text NOT NULL CHECK (state IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    expires_at timestamptz NOT NULL,
    accepted_at timestamptz,
    accepted_by uuid REFERENCES public.account(id),
    accept_key_digest text,
    accept_body_hash text,
    accept_requires_sign_in boolean NOT NULL DEFAULT false,
    accepted_credential_revision bigint,
    accepted_portal_link_id uuid,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (practice_id, record_id) REFERENCES public.patient_record(practice_id, id),
    CONSTRAINT ck_invitation_purpose_fields CHECK (
        (purpose = 'TEAM_MEMBERSHIP' AND record_id IS NULL)
        OR (purpose = 'PATIENT_PORTAL' AND record_id IS NOT NULL AND capabilities = '{}')
    ),
    CONSTRAINT ck_invitation_replay_pair CHECK (
        (accept_key_digest IS NULL) = (accept_body_hash IS NULL))
);

CREATE INDEX invitation_practice_state ON public.invitation (practice_id, state);
CREATE INDEX invitation_expiry ON public.invitation (expires_at);

ALTER TABLE public.invitation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invitation FORCE ROW LEVEL SECURITY;
CREATE POLICY invitation_practice ON public.invitation TO etaye_owner
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);
CREATE POLICY invitation_bootstrap ON public.invitation FOR SELECT TO etaye_owner
    USING (token_digest = current_setting('etaye.invitation_digest', true));

CREATE FUNCTION public.find_invitation_by_digest(token text)
RETURNS TABLE (
    practice_name text, purpose text, recipient_hint text, eligibility text,
    expires_at timestamptz, accepted_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $$
DECLARE
    previous_digest text := coalesce(current_setting('etaye.invitation_digest', true), '');
BEGIN
    PERFORM set_config('etaye.invitation_digest', token, true);
    RETURN QUERY
        SELECT p.display_name::text, i.purpose,
               left(i.intended_recipient, 1) || '•••@' || split_part(i.intended_recipient, '@', 2),
               CASE WHEN i.state = 'REVOKED' THEN 'REVOKED'
                    WHEN i.state = 'EXPIRED' OR (i.state = 'PENDING' AND i.expires_at <= clock_timestamp())
                        THEN 'EXPIRED'
                    WHEN p.state <> 'ACTIVE' THEN 'UNAVAILABLE'
                    WHEN i.state = 'ACCEPTED' THEN 'ALREADY_USED'
                    ELSE 'USABLE' END,
               i.expires_at, i.accepted_at
        FROM public.invitation i
        JOIN public.practice p ON p.id = i.practice_id
        WHERE i.token_digest = token;
    PERFORM set_config('etaye.invitation_digest', previous_digest, true);
END
$$;

CREATE FUNCTION public.apply_invitation(
    target_practice uuid, token text, key_digest text, body_hash text,
    display_name text, password_hash text, birth_date date,
    signed_in_account uuid, signed_in_revision bigint, correlation text
)
RETURNS TABLE (outcome text, account_id uuid, portal_link_id uuid, credential_revision bigint)
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
DECLARE
    inv public.invitation%ROWTYPE;
    actor public.account%ROWTYPE;
    existing_membership public.membership%ROWTYPE;
    practice_state text;
    target_birth_date date;
    derived_name text;
    granted_link uuid;
    new_version bigint;
BEGIN
    SELECT * INTO inv FROM public.invitation i
    WHERE i.practice_id = target_practice AND i.token_digest = token FOR UPDATE;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    IF inv.state = 'REVOKED' THEN
        RETURN QUERY SELECT 'REVOKED'::text, NULL::uuid, NULL::uuid, NULL::bigint;
        RETURN;
    END IF;

    IF inv.state = 'EXPIRED' OR (inv.state = 'PENDING' AND inv.expires_at <= clock_timestamp()) THEN
        RETURN QUERY SELECT 'EXPIRED'::text, NULL::uuid, NULL::uuid, NULL::bigint;
        RETURN;
    END IF;

    -- Keep practice suspension and grant activation ordered in the same transaction.
    SELECT p.state INTO practice_state FROM public.practice p WHERE p.id = inv.practice_id FOR SHARE;
    IF practice_state IS DISTINCT FROM 'ACTIVE' THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::text, NULL::uuid, NULL::uuid, NULL::bigint;
        RETURN;
    END IF;

    IF inv.state = 'ACCEPTED' THEN
        IF inv.accept_key_digest = key_digest
           AND (signed_in_account IS NULL OR signed_in_account = inv.accepted_by)
           AND (NOT inv.accept_requires_sign_in OR signed_in_account = inv.accepted_by)
           AND inv.expires_at > clock_timestamp() THEN
            IF inv.accept_body_hash = body_hash THEN
                RETURN QUERY SELECT 'REPLAY'::text, inv.accepted_by, inv.accepted_portal_link_id,
                    coalesce(signed_in_revision, inv.accepted_credential_revision);
            ELSE
                RETURN QUERY SELECT 'IDEMPOTENCY_CONFLICT'::text, NULL::uuid, NULL::uuid, NULL::bigint;
            END IF;
        ELSE
            RETURN QUERY SELECT 'ALREADY_USED'::text, NULL::uuid, NULL::uuid, NULL::bigint;
        END IF;
        RETURN;
    END IF;

    IF inv.purpose = 'PATIENT_PORTAL' THEN
        -- Serialize invitations to the same record and verify current demographics before any writes.
        SELECT pa.date_of_birth, left(pa.first_name || ' ' || pa.last_name, 200)
        INTO target_birth_date, derived_name
        FROM public.patient_record pr
        JOIN public.patient pa ON pa.id = pr.patient_id AND pa.practice_id = pr.practice_id
        WHERE pr.id = inv.record_id AND pr.practice_id = inv.practice_id
        FOR UPDATE OF pr, pa;

        IF NOT FOUND OR birth_date IS DISTINCT FROM target_birth_date THEN
            RETURN QUERY SELECT 'MISMATCH'::text, NULL::uuid, NULL::uuid, NULL::bigint;
            RETURN;
        END IF;
    END IF;

    PERFORM pg_advisory_xact_lock(51009, hashtext(inv.intended_recipient));
    SELECT * INTO actor FROM public.account a
    WHERE a.normalized_email = inv.intended_recipient FOR UPDATE;
    IF (actor.id IS NOT NULL AND (actor.state <> 'ACTIVE'
            OR signed_in_account IS DISTINCT FROM actor.id
            OR signed_in_revision IS DISTINCT FROM actor.credential_revision))
        OR (actor.id IS NULL AND signed_in_account IS NOT NULL) THEN
        RETURN QUERY SELECT 'SIGN_IN_REQUIRED'::text, NULL::uuid, NULL::uuid, NULL::bigint;
        RETURN;
    END IF;

    IF inv.purpose = 'PATIENT_PORTAL' AND EXISTS (
        SELECT 1 FROM public.portal_link l
        WHERE l.practice_id = inv.practice_id AND l.record_id = inv.record_id AND l.state = 'ACTIVE'
    ) THEN
        RETURN QUERY SELECT 'LINK_EXISTS'::text, NULL::uuid, NULL::uuid, NULL::bigint;
        RETURN;
    END IF;

    IF actor.id IS NULL THEN
        IF inv.purpose = 'TEAM_MEMBERSHIP' THEN
            IF display_name IS NULL OR display_name = '' THEN
                RETURN QUERY SELECT 'DISPLAY_NAME_REQUIRED'::text, NULL::uuid, NULL::uuid, NULL::bigint;
                RETURN;
            END IF;
            derived_name := display_name;
        END IF;
        IF password_hash IS NULL THEN
            RETURN QUERY SELECT 'PASSWORD_REQUIRED'::text, NULL::uuid, NULL::uuid, NULL::bigint;
            RETURN;
        END IF;
    ELSIF inv.purpose = 'TEAM_MEMBERSHIP' THEN
        SELECT * INTO existing_membership FROM public.membership m
        WHERE m.account_id = actor.id AND m.practice_id = inv.practice_id FOR UPDATE;
        IF FOUND THEN
            IF existing_membership.state <> 'ACTIVE' THEN
                RETURN QUERY SELECT 'SIGN_IN_REQUIRED'::text, NULL::uuid, NULL::uuid, NULL::bigint;
                RETURN;
            END IF;
            IF NOT (existing_membership.capabilities @> inv.capabilities
                    AND existing_membership.capabilities <@ inv.capabilities) THEN
                RETURN QUERY SELECT 'MEMBERSHIP_EXISTS'::text, NULL::uuid, NULL::uuid, NULL::bigint;
                RETURN;
            END IF;
        END IF;
    END IF;

    -- The invitation may have expired while waiting for the patient or recipient locks.
    IF inv.expires_at <= clock_timestamp() THEN
        RETURN QUERY SELECT 'EXPIRED'::text, NULL::uuid, NULL::uuid, NULL::bigint;
        RETURN;
    END IF;

    IF actor.id IS NULL THEN
        INSERT INTO public.account (normalized_email, display_name, password_hash, state)
        VALUES (inv.intended_recipient, derived_name, password_hash, 'ACTIVE')
        RETURNING * INTO actor;
    END IF;

    IF inv.purpose = 'TEAM_MEMBERSHIP' AND existing_membership.id IS NULL THEN
        INSERT INTO public.membership (account_id, practice_id, state, capabilities)
        VALUES (actor.id, inv.practice_id, 'ACTIVE', inv.capabilities);
    ELSIF inv.purpose = 'PATIENT_PORTAL' THEN
        INSERT INTO public.portal_link (account_id, practice_id, record_id, verified_by, state)
        VALUES (actor.id, inv.practice_id, inv.record_id, inv.issuer_id, 'ACTIVE')
        RETURNING id INTO granted_link;
    END IF;

    new_version := inv.version + 1;
    UPDATE public.invitation i SET
        state = 'ACCEPTED', accepted_at = clock_timestamp(), accepted_by = actor.id,
        accept_key_digest = key_digest, accept_body_hash = body_hash,
        accept_requires_sign_in = signed_in_account IS NOT NULL,
        accepted_credential_revision = actor.credential_revision, accepted_portal_link_id = granted_link,
        version = new_version, updated_at = clock_timestamp()
    WHERE i.id = inv.id;

    INSERT INTO public.audit_event (
        practice_id, actor_account_id, event_type, resource_type, resource_id,
        resource_version, outcome, correlation_id, safe_metadata
    ) VALUES (
        inv.practice_id, actor.id, 'INVITATION_ACCEPTED', 'INVITATION', inv.id,
        new_version, 'GRANTED', correlation,
        jsonb_build_object('purpose', inv.purpose)
    );

    RETURN QUERY SELECT 'ACCEPTED'::text, actor.id, granted_link, actor.credential_revision;
END
$$;

-- Only the digest lookup may select a practice before the operation establishes its context.
CREATE FUNCTION public.accept_invitation(
    token text, key_digest text, body_hash text,
    display_name text, password_hash text, birth_date date,
    signed_in_account uuid, signed_in_revision bigint, correlation text
)
RETURNS TABLE (outcome text, account_id uuid, portal_link_id uuid, credential_revision bigint)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $$
DECLARE
    previous_digest text := coalesce(current_setting('etaye.invitation_digest', true), '');
    previous_practice text := coalesce(current_setting('etaye.practice_id', true), '');
    target_practice uuid;
BEGIN
    PERFORM set_config('etaye.invitation_digest', token, true);
    SELECT i.practice_id INTO target_practice FROM public.invitation i WHERE i.token_digest = token;
    PERFORM set_config('etaye.invitation_digest', previous_digest, true);

    IF target_practice IS NOT NULL THEN
        PERFORM set_config('etaye.practice_id', target_practice::text, true);
        RETURN QUERY SELECT * FROM public.apply_invitation(target_practice, token, key_digest, body_hash,
            display_name, password_hash, birth_date, signed_in_account, signed_in_revision, correlation);
        PERFORM set_config('etaye.practice_id', previous_practice, true);
    END IF;
END
$$;

REVOKE ALL ON FUNCTION public.apply_invitation(uuid, text, text, text, text, text, date, uuid, bigint, text)
    FROM PUBLIC;

REVOKE ALL ON FUNCTION public.find_invitation_by_digest(text),
    public.accept_invitation(text, text, text, text, text, date, uuid, bigint, text)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.find_invitation_by_digest(text),
    public.accept_invitation(text, text, text, text, text, date, uuid, bigint, text)
    TO etaye_identity;
