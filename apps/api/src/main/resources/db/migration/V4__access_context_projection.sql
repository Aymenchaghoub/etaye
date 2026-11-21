-- Current account-scoped metadata for the practice chooser.
CREATE FUNCTION public.current_access_contexts(actor uuid)
RETURNS TABLE (grant_id uuid, grant_type text, practice_id uuid, practice_name text, display_role text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
AS $$
    WITH active_grants AS (
        SELECT grants.grant_id, grants.grant_type
        FROM public.current_session_grants(actor) grants
        JOIN public.account a ON a.id = actor AND a.state = 'ACTIVE'
    )
    SELECT g.grant_id, g.grant_type, p.id, p.display_name::text,
        CASE
            WHEN m.capabilities && ARRAY['CLINICAL_READ', 'CLINICAL_WRITE', 'CLINICAL_VALIDATE',
                    'PATIENT_PUBLISH', 'CLINICAL_AUDIT'] THEN 'CLINICAL'
            WHEN m.capabilities && ARRAY['TEAM_MANAGE', 'PRACTICE_SETTINGS', 'ADMIN_AUDIT'] THEN 'ADMINISTRATION'
            WHEN 'SCHEDULE_MANAGE' = ANY(m.capabilities) THEN 'SCHEDULING'
            ELSE 'TEAM'
        END
    FROM active_grants g
    JOIN public.membership m ON g.grant_type = 'MEMBERSHIP' AND m.id = g.grant_id
    JOIN public.practice p ON p.id = m.practice_id
    UNION ALL
    SELECT g.grant_id, g.grant_type, p.id, p.display_name::text, 'PATIENT'
    FROM active_grants g
    JOIN public.portal_link link ON g.grant_type = 'PORTAL' AND link.id = g.grant_id
    JOIN public.practice p ON p.id = link.practice_id
$$;

REVOKE ALL ON FUNCTION public.current_access_contexts(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.current_access_contexts(uuid) TO etaye_identity;
