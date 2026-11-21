package dev.aymen.etaye.identity.service;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.repository.InvitationAccessRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvitationAccessService {

    private final InvitationAccessRepository repository;
    private final IdentityLookupService identity;
    private final AccountAccessService accounts;

    public InvitationAccessService(InvitationAccessRepository repository, IdentityLookupService identity,
                                   AccountAccessService accounts) {
        this.repository = repository;
        this.identity = identity;
        this.accounts = accounts;
    }

    public Optional<Preview> inspect(String token) {
        return repository.findByDigest(digest(token)).map(row -> new Preview(row.purpose(), row.practiceName(),
                row.recipientHint(), row.eligibility(), row.expiresAt().toInstant(),
                row.acceptedAt() == null ? null : row.acceptedAt().toInstant()));
    }

    public ActivationResult accept(String token, String idempotencyKey, String canonicalBody,
                                   String displayName, String password, LocalDate birthDate,
                                   AuthenticatedAccount signedIn, String correlationId) {
        String tokenDigest = digest(token);
        if (repository.findByDigest(tokenDigest).isEmpty()) {
            return new ActivationResult("UNKNOWN_TOKEN", null, null);
        }
        String passwordHash = password == null || signedIn != null ? null : accounts.encodePassword(password);
        var acceptance = repository.accept(tokenDigest, digest(idempotencyKey), digest(canonicalBody),
                displayName == null ? null : displayName.strip(), passwordHash, birthDate,
                signedIn == null ? null : signedIn.account().id(),
                signedIn == null ? null : signedIn.account().credentialRevision(), correlationId);
        if (acceptance.accountId() == null) {
            return new ActivationResult(acceptance.outcome(), null, null);
        }
        var account = identity.resolveAccountAndMemberships(acceptance.accountId())
                .filter(resolved -> resolved.account().isActive()
                        && resolved.account().credentialRevision() == acceptance.credentialRevision())
                .orElse(null);
        if (account == null) {
            return new ActivationResult("SIGN_IN_REQUIRED", null, null);
        }
        UUID portalLink = acceptance.portalLinkId();
        if (portalLink != null && !account.activePortalLinkIds().contains(portalLink)) {
            portalLink = null;
        }
        return new ActivationResult(acceptance.outcome(), account, portalLink);
    }

    public record Preview(String purpose, String practiceName, String recipientHint, String eligibility,
                          Instant expiresAt, Instant acceptedAt) {}

    public record ActivationResult(String outcome, AuthenticatedAccount account, UUID portalLinkId) {}

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
