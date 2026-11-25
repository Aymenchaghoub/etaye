package dev.aymen.etaye.records;

import dev.aymen.etaye.records.domain.AssertionKind;
import dev.aymen.etaye.records.domain.SectionState;
import dev.aymen.etaye.records.domain.SectionStateRule;
import dev.aymen.etaye.records.domain.SectionStateRule.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The BR-008 section-state rule as one table of cases, then exhaustively over small combinations (C16). */
class SectionStateRuleTest {

    private static final Instant T0 = Instant.parse("2026-09-14T07:45:00Z");

    private static Statement at(AssertionKind kind, int day) {
        return new Statement(kind, T0.plusSeconds(day * 86_400L), UUID.nameUUIDFromBytes((kind + "-" + day).getBytes()));
    }

    @Test
    @DisplayName("No statement is NOT_RECORDED: a silence is never an absence")
    void emptyIsNotRecorded() {
        assertThat(SectionStateRule.derive(List.of())).isEqualTo(SectionState.NOT_RECORDED);
    }

    @Test
    @DisplayName("The table of BR-008, one row at a time")
    void tableOfCases() {
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.UNKNOWN, 0)))).isEqualTo(SectionState.UNKNOWN);
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.NOT_ASKED, 0)))).isEqualTo(SectionState.NOT_ASKED);
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.EXPLICITLY_ABSENT, 0)))).isEqualTo(SectionState.EXPLICITLY_ABSENT);
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.PRESENT, 0)))).isEqualTo(SectionState.PRESENT);
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.PRESENT, 0), at(AssertionKind.PRESENT, 1)))).isEqualTo(SectionState.PRESENT);
    }

    @Test
    @DisplayName("A dated negative never negates a newer present report, and the order does not matter")
    void conflictInEitherOrder() {
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.EXPLICITLY_ABSENT, 0), at(AssertionKind.PRESENT, 49))))
                .isEqualTo(SectionState.CONFLICTING);
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.PRESENT, 0), at(AssertionKind.EXPLICITLY_ABSENT, 49))))
                .isEqualTo(SectionState.CONFLICTING);
    }

    @Test
    @DisplayName("The latest of UNKNOWN and NOT_ASKED wins when nothing else is stated")
    void latestUnknownWins() {
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.UNKNOWN, 0), at(AssertionKind.NOT_ASKED, 3))))
                .isEqualTo(SectionState.NOT_ASKED);
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.NOT_ASKED, 0), at(AssertionKind.UNKNOWN, 3))))
                .isEqualTo(SectionState.UNKNOWN);
    }

    @Test
    @DisplayName("UNKNOWN or NOT_ASKED beside a present or negative statement gives the state of that statement")
    void unknownBesideStatedFacts() {
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.UNKNOWN, 5), at(AssertionKind.PRESENT, 0)))).isEqualTo(SectionState.PRESENT);
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.NOT_ASKED, 5), at(AssertionKind.EXPLICITLY_ABSENT, 0))))
                .isEqualTo(SectionState.EXPLICITLY_ABSENT);
        assertThat(SectionStateRule.derive(List.of(at(AssertionKind.UNKNOWN, 5), at(AssertionKind.PRESENT, 0), at(AssertionKind.EXPLICITLY_ABSENT, 2))))
                .isEqualTo(SectionState.CONFLICTING);
    }

    @Test
    @DisplayName("Every combination of up to four statements follows the rule whatever the input order")
    void exhaustiveCombinationsAreOrderIndependent() {
        AssertionKind[] kinds = AssertionKind.values();
        Random random = new Random(32);
        int checked = 0;
        for (int size = 1; size <= 4; size++) {
            int combinations = (int) Math.pow(kinds.length, size);
            for (int code = 0; code < combinations; code++) {
                List<Statement> statements = new ArrayList<>();
                int rest = code;
                for (int i = 0; i < size; i++) {
                    statements.add(at(kinds[rest % kinds.length], i));
                    rest /= kinds.length;
                }
                SectionState expected = expected(statements);
                assertThat(SectionStateRule.derive(statements)).as(statements.toString()).isEqualTo(expected);
                List<Statement> shuffled = new ArrayList<>(statements);
                Collections.shuffle(shuffled, random);
                assertThat(SectionStateRule.derive(shuffled)).as("shuffled " + statements).isEqualTo(expected);
                checked++;
            }
        }
        assertThat(checked).isEqualTo(4 + 16 + 64 + 256);
    }

    /** An independent oracle written from the sentence of BR-008, not from the implementation. */
    private static SectionState expected(List<Statement> statements) {
        boolean present = statements.stream().anyMatch(s -> s.kind() == AssertionKind.PRESENT);
        boolean absent = statements.stream().anyMatch(s -> s.kind() == AssertionKind.EXPLICITLY_ABSENT);
        if (present && absent) return SectionState.CONFLICTING;
        if (present) return SectionState.PRESENT;
        if (absent) return SectionState.EXPLICITLY_ABSENT;
        Statement latest = statements.getFirst();
        for (Statement s : statements) {
            if (s.recordedAt().isAfter(latest.recordedAt())) latest = s;
        }
        return latest.kind() == AssertionKind.NOT_ASKED ? SectionState.NOT_ASKED : SectionState.UNKNOWN;
    }
}
