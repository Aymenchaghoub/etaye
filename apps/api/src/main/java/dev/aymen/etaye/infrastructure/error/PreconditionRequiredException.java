package dev.aymen.etaye.infrastructure.error;

/** A mutation that requires If-Match arrived without it (428 PRECONDITION_REQUIRED). */
public class PreconditionRequiredException extends RuntimeException {
    public PreconditionRequiredException(String message) {
        super(message);
    }
}
