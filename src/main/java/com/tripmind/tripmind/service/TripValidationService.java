package com.tripmind.tripmind.service;

import com.tripmind.tripmind.dto.TripRiskView;
import com.tripmind.tripmind.model.TripRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TripValidationService {

    private final Clock clock;

    public TripValidationService(Clock clock) {
        this.clock = clock;
    }

    public ValidationResult validate(TripRequest request) {
        List<TripRiskView> blockers = new ArrayList<>();
        List<TripRiskView> warnings = new ArrayList<>();

        LocalDate departure = parseDate(request.departureDate(), "departureDate", blockers);
        LocalDate returning = parseDate(request.returnDate(), "returnDate", blockers);
        LocalDate today = LocalDate.now(clock);

        if (request.travelers() < 1) {
            blockers.add(risk("blocker", "INVALID_TRAVELERS", "Traveler count must be at least 1."));
        }
        if (request.travelers() > 12) {
            warnings.add(risk("warning", "GROUP_SIZE", "Large group planning usually needs room split and transport confirmation."));
        }
        if (request.budget() < 1000) {
            blockers.add(risk("blocker", "INVALID_BUDGET", "Budget must be at least ₹1,000."));
        }
        if (request.tripType() == null || request.tripType().isBlank()) {
            blockers.add(risk("blocker", "MISSING_TRIP_TYPE", "Trip type must be confirmed as round trip or one way before planning flights."));
        }
        if (request.returnDate() == null || request.returnDate().isBlank()) {
            blockers.add(risk("blocker", "MISSING_RETURN_DATE", "Return date is required so TripMind can estimate stay duration and booking links accurately."));
        }
        if (samePlace(request.origin(), request.destination())) {
            blockers.add(risk("blocker", "SAME_ORIGIN_DESTINATION", "Origin and destination look the same, so a trip route cannot be planned."));
        }
        if ("luxury".equalsIgnoreCase(request.vibe()) && request.budget() / Math.max(1, request.travelers()) < 25_000) {
            blockers.add(risk("blocker", "LUXURY_BUDGET_CONFLICT", "Luxury or 5-star preferences conflict with the current budget. Increase the budget or relax the stay preference."));
        }
        if (request.specialRequest() != null && request.specialRequest().toLowerCase(Locale.ENGLISH).contains("5-star")
                && request.budget() / Math.max(1, request.travelers()) < 25_000) {
            blockers.add(risk("blocker", "FIVE_STAR_BUDGET_CONFLICT", "A 5-star hotel request is not feasible at the current budget level."));
        }

        if (departure != null && returning != null) {
            long days = ChronoUnit.DAYS.between(departure, returning);
            if (days < 1) {
                blockers.add(risk("blocker", "INVALID_DATE_RANGE", "Return date must be after departure date."));
            } else if (days > 90) {
                blockers.add(risk("blocker", "TRIP_TOO_LONG", "Trips longer than 90 days need a different long-stay planning flow."));
            } else if (days > 21) {
                warnings.add(risk("warning", "LONG_TRIP", "This is a long trip, so accommodation and daily-spend estimates should be verified carefully."));
            }
            if (departure.isBefore(today)) {
                warnings.add(risk("warning", "PAST_DEPARTURE_DATE", "Departure date is before today's date; this may be a demo date or typo."));
            }
            if (ChronoUnit.DAYS.between(today, departure) > 14) {
                warnings.add(risk("warning", "FAR_FUTURE_WEATHER", "Weather beyond the normal forecast window is estimate-led and should be treated as seasonal guidance only."));
            }
        }

        if (request.budget() / Math.max(1, request.travelers()) < 5000) {
            warnings.add(risk("warning", "VERY_LOW_PER_PERSON_BUDGET", "Budget per traveler is very low, so the plan may require major compromises."));
        }
        if ("per_person".equalsIgnoreCase(request.budgetScope())) {
            warnings.add(risk("warning", "PER_PERSON_BUDGET", "Budget was interpreted as per-person and normalized to a group total for planning."));
        }
        if (!"INR".equalsIgnoreCase(request.budgetCurrency())) {
            warnings.add(risk("warning", "CURRENCY_NORMALIZED", "Budget was normalized from " + request.budgetCurrency() + " into INR planning estimates."));
        }
        if (request.travelerNeeds() != null && !request.travelerNeeds().isBlank()) {
            warnings.add(risk("warning", "SPECIAL_TRAVELER_NEEDS", "Traveler needs were captured and should be verified against live hotel, restaurant, and transport filters."));
        }

        return new ValidationResult(blockers, warnings);
    }

    private LocalDate parseDate(String value, String field, List<TripRiskView> blockers) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            blockers.add(risk("blocker", "INVALID_" + field.toUpperCase(Locale.ENGLISH), field + " must use ISO format yyyy-MM-dd."));
            return null;
        }
    }

    private boolean samePlace(String origin, String destination) {
        return normalizePlace(origin).equals(normalizePlace(destination));
    }

    private String normalizePlace(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
    }

    private TripRiskView risk(String severity, String code, String message) {
        return new TripRiskView(severity, code, message);
    }

    public record ValidationResult(
            List<TripRiskView> blockers,
            List<TripRiskView> warnings
    ) {
        public boolean hasBlockers() {
            return !blockers.isEmpty();
        }
    }
}
