package dev.aymen.etaye.communications.push;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class PushNotifications {

    private final PushPort port;

    public PushNotifications(PushPort port) {
        this.port = port;
    }

    public void dispatch(JsonNode payload) {
        var kind = PushPort.NotificationKind.INFORMATION_AVAILABLE;
        if (payload == null || !payload.isObject() || payload.size() != 1
                || !payload.path("kind").isString()
                || !kind.name().equals(payload.path("kind").stringValue())) {
            throw new IllegalArgumentException("Expected a content-free push notification");
        }
        port.send(kind);
    }
}
