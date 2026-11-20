package dev.aymen.etaye.communications.push;

@FunctionalInterface
public interface PushPort {
    void send(NotificationKind kind);

    enum NotificationKind {
        INFORMATION_AVAILABLE
    }
}
