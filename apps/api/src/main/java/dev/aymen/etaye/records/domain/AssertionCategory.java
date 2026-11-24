package dev.aymen.etaye.records.domain;

import java.util.List;

/** The three history sections, in the order every consumer presents them. */
public enum AssertionCategory {
    ALLERGY, HISTORY, REPORTED_TREATMENT;

    public static final List<AssertionCategory> DISPLAY_ORDER = List.of(ALLERGY, HISTORY, REPORTED_TREATMENT);
}
