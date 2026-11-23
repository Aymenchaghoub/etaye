package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Identity of a registration. confirmedDistinctRecordIds is the human confirmation that every named
 * likely-duplicate candidate is a different person (UD-63); the server re-checks it at commit.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PatientRegistration(
        @NotBlank(message = "firstName is required")
        @Size(min = 1, max = 100, message = "firstName must be between 1 and 100 characters")
        @Pattern(regexp = ".*[\\p{L}\\p{N}].*", message = "firstName must contain a letter or a digit")
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "firstName must not contain control characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(min = 1, max = 100, message = "lastName must be between 1 and 100 characters")
        @Pattern(regexp = ".*[\\p{L}\\p{N}].*", message = "lastName must contain a letter or a digit")
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "lastName must not contain control characters")
        String lastName,

        @JsonDeserialize(using = PatientEdit.StrictLocalDateDeserializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull(message = "dateOfBirth is required")
        @PastOrPresent(message = "dateOfBirth must not be in the future")
        LocalDate dateOfBirth,

        @Email(message = "contactEmail must be a valid email address")
        @Pattern(regexp = ".+", message = "contactEmail must not be empty")
        @Size(max = 255, message = "contactEmail must not exceed 255 characters")
        String contactEmail,

        @Size(max = 40, message = "phone must not exceed 40 characters")
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "phone must not contain control characters")
        String phone,

        @Size(max = 10, message = "confirmedDistinctRecordIds must not exceed 10 entries")
        List<UUID> confirmedDistinctRecordIds
) {
    public List<UUID> confirmations() {
        return confirmedDistinctRecordIds == null ? List.of() : confirmedDistinctRecordIds;
    }
}
