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

    private final AccountAccessRepository repository;
    private final IdentityLookupService identity;
    private final Optional<LocalRecoveryInbox> inbox;
    private final Pbkdf2PasswordEncoder passwordEncoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final SecureRandom random = new SecureRandom();
    private final String dummyPasswordHash = passwordEncoder.encode(randomToken());

    public AccountAccessService(AccountAccessRepository repository, IdentityLookupService identity,
                                ObjectProvider<LocalRecoveryInbox> inbox) {
        this.repository = repository;
        this.identity = identity;
        this.inbox = Optional.ofNullable(inbox.getIfAvailable());
    }

    public Optional<AuthenticatedAccount> signIn(String email, String password, String correlationId) {
        Optional<Credential> credential = repository.findCredential(email)
                .filter(value -> PASSWORD_ENCODING.matcher(value.encodedPassword()).matches());
        boolean matches = passwordEncoder.matches(password, credential.map(Credential::encodedPassword)
                .orElse(dummyPasswordHash));
        if (!matches || credential.isEmpty()) {
            return Optional.empty();
        }
        Credential valid = credential.get();
        return identity.resolveAccountAndMemberships(valid.accountId())
                .filter(account -> account.account().isActive()
                        && account.account().credentialRevision() == valid.revision()
                        && repository.recordSignIn(valid.accountId(), valid.revision(), correlationId));
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
        boolean redeemed = repository.redeemRecovery(digest(token), passwordEncoder.encode(newPassword), correlationId);
        if (redeemed) {
            inbox.ifPresent(delivery -> delivery.remove(token));
        }
        return redeemed;
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
