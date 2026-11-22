CREATE OR REPLACE FUNCTION public.accept_invitation(
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
    activation record;
BEGIN
    PERFORM set_config('etaye.invitation_digest', token, true);
    SELECT i.practice_id INTO target_practice FROM public.invitation i WHERE i.token_digest = token;
    PERFORM set_config('etaye.invitation_digest', previous_digest, true);

    IF target_practice IS NOT NULL THEN
        PERFORM set_config('etaye.practice_id', target_practice::text, true);
        BEGIN
            SELECT * INTO activation FROM public.apply_invitation(target_practice, token, key_digest, body_hash,
                display_name, password_hash, birth_date, signed_in_account, signed_in_revision, correlation);
            IF FOUND THEN
                -- Grant inserts can wait on foreign keys after the pre-write expiry check.
                IF activation.outcome = 'ACCEPTED' AND EXISTS (
                    SELECT 1 FROM public.invitation i
                    WHERE i.practice_id = target_practice AND i.token_digest = token
                      AND i.accepted_at >= i.expires_at
                ) THEN
                    RAISE SQLSTATE 'ET001' USING MESSAGE = 'Invitation expired before activation';
                END IF;
                RETURN QUERY SELECT activation.outcome, activation.account_id,
                    activation.portal_link_id, activation.credential_revision;
            END IF;
        EXCEPTION WHEN SQLSTATE 'ET001' THEN
            RETURN QUERY SELECT 'EXPIRED'::text, NULL::uuid, NULL::uuid, NULL::bigint;
        END;
        PERFORM set_config('etaye.practice_id', previous_practice, true);
    END IF;
END
$$;
