package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.aymen.etaye.records.domain.EventDateView;

import java.time.Instant;
import java.time.LocalDate;

/** The contract EventDate: an unknown date carries no instant and no date. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventDateResponse(String precision, Instant instant, LocalDate date, String timezone) {

    public static EventDateResponse of(EventDateView view) {
        return new EventDateResponse(view.precision().name(), view.instant(), view.date(), view.timezone());
    }
}
