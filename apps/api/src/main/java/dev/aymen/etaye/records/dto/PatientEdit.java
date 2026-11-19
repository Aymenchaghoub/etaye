package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record PatientEdit(
        @NotBlank(message = "firstName is required")
        @Size(min = 1, max = 100, message = "firstName must be between 1 and 100 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(min = 1, max = 100, message = "lastName must be between 1 and 100 characters")
        String lastName,

        @NotNull(message = "dateOfBirth is required")
        @PastOrPresent(message = "dateOfBirth must not be in the future")
        LocalDate dateOfBirth,

        @Email(message = "contactEmail must be a valid email address")
        @Size(max = 255, message = "contactEmail must not exceed 255 characters")
        String contactEmail,

        @Size(max = 40, message = "phone must not exceed 40 characters")
        String phone
) {}
