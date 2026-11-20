package dev.aymen.etaye.identity.service;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.repository.AccountAccessRepository;
import dev.aymen.etaye.identity.repository.AccountAccessRepository.Credential;
import dev.aymen.etaye.identity.recovery.LocalRecoveryInbox;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AccountAccessService {

    private static final Pattern PASSWORD_ENCODING = Pattern.compile("[0-9a-f]{96}");
    private static final Pattern RECOVERY_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final String PASSWORD_VERSION = "{pbkdf2-sha256-600000}";

    private final AccountAccessRepository repository;
    private final IdentityLookupService identity;
    private final Optional<LocalRecoveryInbox> inbox;
    private final Pbkdf2PasswordEncoder passwordEncoder = new Pbkdf2PasswordEncoder("", 16, 600_000,
            Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    private final Pbkdf2PasswordEncoder legacyPasswordEncoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final SecureRandom random = new SecureRandom();
    private final String dummyPasswordHash = passwordEncoder.encode(randomToken());
    private final String dummyLegacyHash = legacyPasswordEncoder.encode(randomToken());

    public AccountAccessService(AccountAccessRepository repository, IdentityLookupService identity,
                                ObjectProvider<LocalRecoveryInbox> inbox) {
        this.repository = repository;
        this.identity = identity;
        this.inbox = Optional.ofNullable(inbox.getIfAvailable());
    }

    public Optional<AuthenticatedAccount> signIn(String email, String password, String correlationId) {
        Optional<Credential> credential = repository.findCredential(email);
        boolean matches = matchesPassword(password, credential.map(Credential::encodedPassword).orElse(""));
        if (!matches || credential.isEmpty()) {
            return Optional.empty();
        }
        Credential valid = credential.get();
        if (!repository.recordSignIn(valid.accountId(), valid.revision(), correlationId)) {
            return Optional.empty();
        }
        return identity.resolveAccountAndMemberships(valid.accountId())
                .filter(account -> account.account().isActive()
                        && account.account().credentialRevision() == valid.revision());
    }

    public void requestRecovery(String email, String key) {
        String token = randomToken();
        repository.startRecovery(email, digest(key + "\n" + email), digest(token))
                .ifPresent(expiresAt -> inbox.ifPresent(delivery -> delivery.capture(email, token, expiresAt)));
    }

    public boolean resetPassword(String token, String newPassword, String correlationId) {
        if (!RECOVERY_TOKEN.matcher(token).matches()) {
            return false;
        }
        String encoded = PASSWORD_VERSION + passwordEncoder.encode(newPassword);
        boolean redeemed = repository.redeemRecovery(digest(token), encoded, correlationId);
        if (redeemed) {
            inbox.ifPresent(delivery -> delivery.remove(token));
        }
        return redeemed;
    }

    private boolean matchesPassword(String password, String encoded) {
        String currentHash = encoded.startsWith(PASSWORD_VERSION) ? encoded.substring(PASSWORD_VERSION.length()) : "";
        boolean currentEncoding = PASSWORD_ENCODING.matcher(currentHash).matches();
        boolean legacyEncoding = PASSWORD_ENCODING.matcher(encoded).matches();

        // Do comparable verification work for absent accounts and both stored encodings.
        boolean currentMatches = passwordEncoder.matches(password, currentEncoding ? currentHash : dummyPasswordHash);
        boolean legacyMatches = legacyPasswordEncoder.matches(password, legacyEncoding ? encoded : dummyLegacyHash);
        return (currentEncoding && currentMatches) || (legacyEncoding && legacyMatches);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
