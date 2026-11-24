package dev.aymen.etaye.records.domain;

/** What a statement says about a fact. A missing statement is never one of these (BR-008). */
public enum AssertionKind {
    PRESENT, EXPLICITLY_ABSENT, UNKNOWN, NOT_ASKED
}
