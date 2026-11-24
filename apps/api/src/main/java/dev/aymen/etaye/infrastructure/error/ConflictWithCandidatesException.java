package dev.aymen.etaye.infrastructure.error;

import java.util.List;

/**
 * A business conflict that lists the existing resources the caller must choose between. The
 * candidates are serialized as the Problem "candidates" member and carry no clinical data; the
 * owning module decides their shape, so this class stays free of any module type.
 */
public class ConflictWithCandidatesException extends RuntimeException {
    private final String code;
    private final List<?> candidates;

    public ConflictWithCandidatesException(String code, String message, List<?> candidates) {
        super(message);
        this.code = code;
        this.candidates = List.copyOf(candidates);
    }

    public String code() {
        return code;
    }

    public List<?> candidates() {
        return candidates;
    }
}
