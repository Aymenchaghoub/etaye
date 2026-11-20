package dev.aymen.etaye.infrastructure.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyServiceTest {

    private final IdempotencyService service = new IdempotencyService(null);

    @Test
    @DisplayName("The canonical body hash does not depend on the map implementation or its comparator")
    void bodyHashIgnoresTheMapOrdering() {
        Map<String, Object> reversed = new TreeMap<>(java.util.Comparator.reverseOrder());
        Map<String, Object> hashed = new HashMap<>();
        for (String key : new String[]{"firstName", "lastName", "phone", "contactEmail"}) {
            reversed.put(key, key + "-value");
            hashed.put(key, key + "-value");
        }
        assertThat(service.computeCanonicalBodyHash(reversed)).isEqualTo(service.computeCanonicalBodyHash(hashed));
    }

    @Test
    @DisplayName("A CREATED receipt without its resource is rejected before any statement runs")
    void createdReceiptRequiresItsResource() {
        assertThatThrownBy(() -> service.recordReceipt(UUID.randomUUID(), UUID.randomUUID(), "createPatient",
                "digest", "hash", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("responseResource");
    }
}
