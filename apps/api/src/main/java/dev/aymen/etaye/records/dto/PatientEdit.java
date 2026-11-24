package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@JsonIgnoreProperties(ignoreUnknown = false)
public record PatientEdit(
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

        @JsonDeserialize(using = StrictLocalDateDeserializer.class)
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
        String phone
) {
    public static class StrictLocalDateDeserializer extends ValueDeserializer<LocalDate> {
        private static final java.util.regex.Pattern STRICT_DATE_PATTERN =
                java.util.regex.Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");

        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            if (!p.hasToken(JsonToken.VALUE_STRING)) {
                ctxt.reportInputMismatch(LocalDate.class, "Invalid date format");
                return null;
            }
            String text = p.getString();
            if (!STRICT_DATE_PATTERN.matcher(text).matches()) {
                ctxt.reportInputMismatch(LocalDate.class, "Invalid date format");
                return null;
            }
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                ctxt.reportInputMismatch(LocalDate.class, "Invalid date format");
                return null;
            }
        }
    }
}
