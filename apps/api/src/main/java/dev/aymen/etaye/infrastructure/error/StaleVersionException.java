package dev.aymen.etaye.infrastructure.error;

/** The If-Match version is not the current version of the aggregate (412 STALE_VERSION). */
public class StaleVersionException extends RuntimeException {
    public StaleVersionException(String message) {
        super(message);
    }
}
