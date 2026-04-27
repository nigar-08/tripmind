package com.tripmind.tripmind.service.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PricingEngineService {

    private static final List<String> PREMIUM_DESTINATION_SIGNALS = List.of(
            "maldives", "switzerland", "london", "paris", "new york", "los angeles", "tokyo", "japan", "australia", "canada"
    );
    private static final List<String> VALUE_DESTINATION_SIGNALS = List.of(
            "goa", "pune", "delhi", "jaipur", "kochi", "kerala", "bangkok", "thailand", "vietnam", "nepal"
    );
    private static final List<String> BEACH_OR_HILL_SIGNALS = List.of(
            "beach", "goa", "bali", "maldives", "andaman", "phuket", "mountain", "manali", "ladakh", "kashmir", "leh"
    );
    private static final List<String> CITY_BREAK_SIGNALS = List.of(
            "dubai", "singapore", "london", "paris", "new york", "delhi", "mumbai", "bangalore", "tokyo"
    );

    private final RestClient restClient;
    private final String geoapifyApiKey;

    public PricingEngineService(
            RestClient.Builder restClientBuilder,
            @Value("${tripmind.places.geoapify.api-key:${GEOAPIFY_API_KEY:}}") String geoapifyApiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.geoapifyApiKey = geoapifyApiKey;
    }

    public int estimateFlightCost(String origin, String destination, int travelers) {
        return estimateFlightPerPerson(origin, destination) * travelers;
    }

    public boolean isInternationalRoute(String origin, String destination) {
        return routeProfile(origin, destination)
                .map(RouteProfile::international)
                .orElseGet(() -> {
                    String normalized = normalize(origin + " " + destination);
                    return hasAny(normalized, PREMIUM_DESTINATION_SIGNALS)
                            || normalized.contains("dubai")
                            || normalized.contains("paris")
                            || normalized.contains("london")
                            || normalized.contains("singapore")
                            || normalized.contains("thailand");
                });
    }

    public int estimateHiddenCosts(String origin, String destination, int travelers, String departureDate) {
        boolean international = isInternationalRoute(origin, destination);
        int transfers = international ? 2200 * travelers : 700 * travelers;
        int baggage = 1200 * travelers;
        int insurance = international ? 1800 * travelers : 500 * travelers;
        int visa = international ? 4500 * travelers : 0;
        int seasonal = "peak".equalsIgnoreCase(seasonalityLabel(departureDate, destination)) ? 1200 * travelers : 0;
        return transfers + baggage + insurance + visa + seasonal;
    }

    public String routeSupportGuidance(String origin, String destination) {
        Optional<RouteProfile> profile = routeProfile(origin, destination);
        if (profile.isPresent()) {
            RouteProfile routeProfile = profile.get();
            if (routeProfile.distanceKm() > 4500) {
                return "This looks like a long-haul route, so consider alternate nearby hubs if direct flights are weak.";
            }
            return "Route can be estimated directly from the current origin and destination pair.";
        }
        return "Live route support is uncertain for this origin/destination pair. If direct options look weak, check the nearest major airport hub as a backup.";
    }

    public int estimateFlightPerPerson(String destination) {
        return estimateFlightPerPerson("", destination);
    }

    public int estimateFlightPerPerson(String origin, String destination) {
        Optional<RouteProfile> routeProfile = routeProfile(origin, destination);
        if (routeProfile.isPresent()) {
            RouteProfile profile = routeProfile.get();
            int base = profile.international() ? 7_500 : 2_200;
            double perKm = profile.international() ? 3.2 : 2.2;
            int minimum = profile.international() ? 12_000 : 2_800;
            return roundedToHundreds(Math.max(minimum, base + (int) Math.round(profile.distanceKm() * perKm)));
        }
        return genericFlightFallback(destination);
    }

    public int estimateHotelCost(String destination, String departureDate, String returnDate, int travelers, String vibe) {
        int nights = stayNights(departureDate, returnDate);
        int rooms = Math.max(1, (travelers + 1) / 2);
        return estimateHotelNightly(destination, departureDate, vibe) * nights * rooms;
    }

    public int estimateHotelNightly(String destination, String departureDate, String vibe) {
        double vibeMultiplier = "luxury".equalsIgnoreCase(vibe) ? 1.5 : "budget".equalsIgnoreCase(vibe) ? 0.8 : 1.0;
        double seasonalMultiplier = seasonMultiplier(departureDate, destination);
        return roundedToHundreds(genericHotelNightly(destination) * vibeMultiplier * seasonalMultiplier);
    }

    public int estimateActivityAndDailySpend(String destination, String departureDate, String returnDate, int travelers, String vibe) {
        long duration = Math.max(1, ChronoUnit.DAYS.between(LocalDate.parse(departureDate), LocalDate.parse(returnDate)));
        return estimateDailyPerPerson(destination, departureDate, vibe) * (int) duration * travelers;
    }

    public int estimateDailyPerPerson(String destination, String departureDate, String vibe) {
        double vibeMultiplier = "luxury".equalsIgnoreCase(vibe) ? 1.3 : "budget".equalsIgnoreCase(vibe) ? 0.85 : 1.0;
        double seasonalMultiplier = seasonMultiplier(departureDate, destination);
        return roundedToHundreds(genericDailySpend(destination) * vibeMultiplier * seasonalMultiplier);
    }

    public String confidence(String destination) {
        return hasGeoapifyKey() ? "medium" : "medium-low";
    }

    public String seasonalityLabel(String departureDate, String destination) {
        Month month = LocalDate.parse(departureDate).getMonth();
        String normalized = normalize(destination);
        if (hasAny(normalized, BEACH_OR_HILL_SIGNALS)) {
            if (List.of(12, 1, 5, 6).contains(month.getValue())) return "peak";
            if (List.of(2, 3, 9, 10, 11).contains(month.getValue())) return "shoulder";
            return "off-season";
        }
        if (hasAny(normalized, CITY_BREAK_SIGNALS)) {
            if (List.of(12, 1, 2).contains(month.getValue())) return "peak";
            if (List.of(3, 4, 10, 11).contains(month.getValue())) return "shoulder";
            return "standard";
        }
        return "standard";
    }

    private int genericFlightFallback(String destination) {
        String normalized = normalize(destination);
        if (hasAny(normalized, PREMIUM_DESTINATION_SIGNALS)) {
            return 40_000;
        }
        if (hasAny(normalized, VALUE_DESTINATION_SIGNALS)) {
            return 6_000;
        }
        if (hasAny(normalized, CITY_BREAK_SIGNALS)) {
            return 18_000;
        }
        return 12_000;
    }

    private int genericHotelNightly(String destination) {
        String normalized = normalize(destination);
        if (hasAny(normalized, PREMIUM_DESTINATION_SIGNALS)) {
            return 8_000;
        }
        if (hasAny(normalized, VALUE_DESTINATION_SIGNALS)) {
            return 2_500;
        }
        if (hasAny(normalized, CITY_BREAK_SIGNALS)) {
            return 5_000;
        }
        return 3_500;
    }

    private int genericDailySpend(String destination) {
        String normalized = normalize(destination);
        if (hasAny(normalized, PREMIUM_DESTINATION_SIGNALS)) {
            return 4_500;
        }
        if (hasAny(normalized, VALUE_DESTINATION_SIGNALS)) {
            return 1_500;
        }
        if (hasAny(normalized, CITY_BREAK_SIGNALS)) {
            return 3_000;
        }
        return 2_000;
    }

    private int stayNights(String departureDate, String returnDate) {
        return (int) Math.max(1, ChronoUnit.DAYS.between(LocalDate.parse(departureDate), LocalDate.parse(returnDate)));
    }

    private double seasonMultiplier(String departureDate, String destination) {
        String label = seasonalityLabel(departureDate, destination);
        return switch (label) {
            case "peak" -> 1.2;
            case "shoulder" -> 1.08;
            case "off-season" -> 0.94;
            default -> 1.0;
        };
    }

    private Optional<RouteProfile> routeProfile(String origin, String destination) {
        Optional<LocationPoint> originPoint = geocode(origin);
        Optional<LocationPoint> destinationPoint = geocode(destination);
        if (originPoint.isEmpty() || destinationPoint.isEmpty()) {
            return Optional.empty();
        }
        double distanceKm = distanceKm(originPoint.get(), destinationPoint.get());
        boolean international = !originPoint.get().countryCode().isBlank()
                && !destinationPoint.get().countryCode().isBlank()
                && !originPoint.get().countryCode().equalsIgnoreCase(destinationPoint.get().countryCode());
        return Optional.of(new RouteProfile(distanceKm, international));
    }

    private Optional<LocationPoint> geocode(String place) {
        if (!hasGeoapifyKey() || place == null || place.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.geoapify.com")
                            .path("/v1/geocode/search")
                            .queryParam("text", place)
                            .queryParam("limit", 1)
                            .queryParam("apiKey", geoapifyApiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("features") instanceof List<?> features) || features.isEmpty()) {
                return Optional.empty();
            }
            if (!(features.getFirst() instanceof Map<?, ?> feature) || !(feature.get("properties") instanceof Map<?, ?> properties)) {
                return Optional.empty();
            }
            if (!(feature.get("geometry") instanceof Map<?, ?> geometry) || !(geometry.get("coordinates") instanceof List<?> coordinates) || coordinates.size() < 2) {
                return Optional.empty();
            }
            if (coordinates.get(0) instanceof Number lon && coordinates.get(1) instanceof Number lat) {
                String countryCode = properties.get("country_code") instanceof String code ? code : "";
                return Optional.of(new LocationPoint(lat.doubleValue(), lon.doubleValue(), countryCode));
            }
            return Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private double distanceKm(LocationPoint origin, LocationPoint destination) {
        double earthRadiusKm = 6_371;
        double latDistance = Math.toRadians(destination.lat() - origin.lat());
        double lonDistance = Math.toRadians(destination.lon() - origin.lon());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(origin.lat())) * Math.cos(Math.toRadians(destination.lat()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private boolean hasGeoapifyKey() {
        return geoapifyApiKey != null && !geoapifyApiKey.isBlank();
    }

    private boolean hasAny(String normalized, List<String> signals) {
        return signals.stream().anyMatch(normalized::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().trim();
    }

    private int roundedToHundreds(double value) {
        return (int) Math.round(value / 100.0) * 100;
    }

    private record LocationPoint(double lat, double lon, String countryCode) {
    }

    private record RouteProfile(double distanceKm, boolean international) {
    }
}
