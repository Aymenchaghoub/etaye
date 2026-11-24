package dev.aymen.etaye.infrastructure.error;

/** A request parameter that is well-formed HTTP but unacceptable, such as a foreign cursor or an out-of-range limit. */
public class InvalidParameterException extends RuntimeException {
    public InvalidParameterException(String message) {
        super(message);
    }
}
