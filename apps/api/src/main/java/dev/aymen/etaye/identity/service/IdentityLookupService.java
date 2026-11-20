package dev.aymen.etaye.identity.service;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.repository.IdentityLookupRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Public service interface for account identity and membership lookup.
 * Encapsulates the internal IdentityLookupRepository so repository types remain private to identity.
 */
@Service
public class IdentityLookupService {

    private final IdentityLookupRepository identityLookupRepository;

    public IdentityLookupService(IdentityLookupRepository identityLookupRepository) {
        this.identityLookupRepository = identityLookupRepository;
    }

    public Optional<AuthenticatedAccount> resolveAccountAndMemberships(UUID accountId) {
        return identityLookupRepository.resolveAccountAndMemberships(accountId);
    }
}
