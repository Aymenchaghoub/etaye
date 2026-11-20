package dev.aymen.etaye.communications.push;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PushNotificationsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<PushPort.NotificationKind> submitted = new ArrayList<>();
    private final PushNotifications notifications = new PushNotifications(submitted::add);

    @Test
    void onlyTheClosedKindCrossesThePort() {
        var payload = mapper.readTree("{\"kind\":\"INFORMATION_AVAILABLE\"}");
        notifications.dispatch(payload);
        new PushNotifications(new NoOpPushPort()).dispatch(payload);
        assertThat(submitted).containsExactly(PushPort.NotificationKind.INFORMATION_AVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"kind\":\"INFORMATION_AVAILABLE\",\"text\":\"Synthetic clinical finding\"}",
            "{\"kind\":\"Synthetic clinical finding\"}",
            "{\"kind\":0}", "{}", "null"
    })
    void rejectsAnyOtherShapeBeforeCallingThePort(String candidate) {
        assertThatIllegalArgumentException().isThrownBy(() -> notifications.dispatch(mapper.readTree(candidate)));
        assertThat(submitted).isEmpty();
    }
}
