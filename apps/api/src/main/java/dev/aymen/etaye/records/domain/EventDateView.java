package dev.aymen.etaye.records.domain;

import java.time.Instant;
import java.time.LocalDate;

/** When a fact happened. An unknown date carries nothing: it is never replaced by the recording date. */
public record EventDateView(Precision precision, Instant instant, LocalDate date, String timezone) {

    public enum Precision { INSTANT, DATE, UNKNOWN }
}
