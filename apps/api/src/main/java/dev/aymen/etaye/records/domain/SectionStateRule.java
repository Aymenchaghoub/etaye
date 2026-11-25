package dev.aymen.etaye.records.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The BR-008 rule that derives the state of a history section from its current statements. It is a pure function
 * with no side effect so that the record screen and the clinician brief (UD-30) can never disagree.
 *
 * <ul>
 *   <li>no statement: NOT_RECORDED, which never means absence;</li>
 *   <li>an EXPLICITLY_ABSENT and a PRESENT statement together, in either order: CONFLICTING, because a dated negative
 *       does not negate a newer contradictory report and nothing is resolved without a human;</li>
 *   <li>only EXPLICITLY_ABSENT (possibly beside UNKNOWN or NOT_ASKED): EXPLICITLY_ABSENT;</li>
 *   <li>at least one PRESENT and no negative: PRESENT;</li>
 *   <li>only UNKNOWN or NOT_ASKED: the latest of them by recording time, then by identifier.</li>
 * </ul>
 */
public final class SectionStateRule {

    /** What the rule needs from one current statement. */
    public record Statement(AssertionKind kind, Instant recordedAt, UUID id) {
    }

    private static final Comparator<Statement> LATEST = Comparator.comparing(Statement::recordedAt)
            .thenComparing(Statement::id);

    private SectionStateRule() {
    }

    public static SectionState derive(List<Statement> current) {
        if (current.isEmpty()) {
            return SectionState.NOT_RECORDED;
        }
        boolean present = current.stream().anyMatch(s -> s.kind() == AssertionKind.PRESENT);
        boolean absent = current.stream().anyMatch(s -> s.kind() == AssertionKind.EXPLICITLY_ABSENT);
        if (present && absent) {
            return SectionState.CONFLICTING;
        }
        if (present) {
            return SectionState.PRESENT;
        }
        if (absent) {
            return SectionState.EXPLICITLY_ABSENT;
        }
        Statement latest = current.stream().max(LATEST).orElseThrow();
        return latest.kind() == AssertionKind.NOT_ASKED ? SectionState.NOT_ASKED : SectionState.UNKNOWN;
    }
}
