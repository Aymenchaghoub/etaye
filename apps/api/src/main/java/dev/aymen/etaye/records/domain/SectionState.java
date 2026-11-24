package dev.aymen.etaye.records.domain;

/**
 * The state of one history section, derived by {@link SectionStateRule} and never stored or typed by a client.
 * CONFLICTING reads as unknown: the section never concludes an absence.
 */
public enum SectionState {
    NOT_RECORDED, UNKNOWN, NOT_ASKED, EXPLICITLY_ABSENT, PRESENT, CONFLICTING
}
