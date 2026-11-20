package dev.aymen.etaye.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountRequestBudgetTest {

    @Test
    void anonymousCsrfHasAGlobalBudgetAcrossDifferentDirectAddresses() {
        var budget = new AccountRequestBudget();
        for (int index = 1; index <= 120; index++) {
            assertThat(budget.retryAfter(AccountRequestBudget.Operation.CSRF, "192.0.2." + index, null)).isZero();
        }
        assertThat(budget.retryAfter(AccountRequestBudget.Operation.CSRF, "198.51.100.1", null))
                .isBetween(1, 60);
    }
}
