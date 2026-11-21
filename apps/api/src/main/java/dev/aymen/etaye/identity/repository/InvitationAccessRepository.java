package dev.aymen.etaye.identity.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InvitationAccessRepository {

    private final JdbcTemplate jdbc;

    public InvitationAccessRepository(@Qualifier("identityJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Preview> findByDigest(String tokenDigest) {
        return jdbc.query("SELECT * FROM public.find_invitation_by_digest(?)",
                (row, index) -> new Preview(row.getString("purpose"), row.getString("practice_name"),
                        row.getString("recipient_hint"), row.getString("eligibility"),
                        row.getObject("expires_at", OffsetDateTime.class),
                        row.getObject("accepted_at", OffsetDateTime.class)), tokenDigest)
                .stream().findFirst();
    }

    public Acceptance accept(String tokenDigest, String keyDigest, String bodyHash, String displayName,
                             String passwordHash, LocalDate birthDate, UUID signedInAccount, Long signedInRevision, String correlationId) {
        return jdbc.query("""
                SELECT * FROM public.accept_invitation(
                    ?::text, ?::text, ?::text, ?::text, ?::text, ?::date, ?::uuid, ?::bigint, ?::text)
                """, (row, index) -> new Acceptance(row.getString("outcome"), row.getObject("account_id", UUID.class),
                row.getObject("portal_link_id", UUID.class), row.getObject("credential_revision", Long.class)),
                tokenDigest, keyDigest, bodyHash, displayName, passwordHash, birthDate, signedInAccount, signedInRevision, correlationId)
                .stream().findFirst().orElseGet(Acceptance::unknownToken);
    }

    public record Preview(String purpose, String practiceName, String recipientHint, String eligibility,
                          OffsetDateTime expiresAt, OffsetDateTime acceptedAt) {}

    public record Acceptance(String outcome, UUID accountId, UUID portalLinkId, Long credentialRevision) {
        public static Acceptance unknownToken() {
            return new Acceptance("UNKNOWN_TOKEN", null, null, null);
        }
    }
}
