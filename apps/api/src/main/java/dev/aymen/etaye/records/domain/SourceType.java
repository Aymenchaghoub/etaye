package dev.aymen.etaye.records.domain;

/** Origin of a fact (BR-008). An acknowledged patient report keeps its original origin. */
public enum SourceType {
    PATIENT_REPORTED, PRACTITIONER_OBSERVED, DOCUMENT_REPORTED
}
