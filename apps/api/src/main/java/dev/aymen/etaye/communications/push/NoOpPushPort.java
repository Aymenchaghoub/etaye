package dev.aymen.etaye.communications.push;

import org.springframework.stereotype.Component;

@Component
final class NoOpPushPort implements PushPort {
    @Override
    public void send(NotificationKind kind) {
        // Delivery and provider setup are owned by the notification story.
    }
}
