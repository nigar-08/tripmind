package com.tripmind.tripmind.service.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PlaceDiscoveryService {

    private static final int SEARCH_RADIUS_METERS = 12_000;

    private final RestClient restClient;
    private final String provider;
    private final String geoapifyApiKey;
    private final String openTripMapApiKey;

    public PlaceDiscoveryService(
            RestClient.Builder restClientBuilder,
            @Value("${tripmind.places.provider:hybrid}") String provider,
            @Value("${tripmind.places.geoapify.api-key:${GEOAPIFY_API_KEY:}}") String geoapifyApiKey,
            @Value("${tripmind.places.opentripmap.api-key:${OPENTRIPMAP_API_KEY:}}") String openTripMapApiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.provider = provider;
        this.geoapifyApiKey = geoapifyApiKey;
        this.openTripMapApiKey = openTripMapApiKey;
    }

    public PlaceDiscovery discover(String destination, String vibe, int limit) {
        if ("disabled".equalsIgnoreCase(provider)) {
            return PlaceDiscovery.empty();
        }

        List<PlaceSuggestion> places = new ArrayList<>();
        Set<String> sources = new LinkedHashSet<>();

        if (useGeoapify()) {
            List<PlaceSuggestion> geoapifyPlaces = geoapifyPlaces(destination, vibe, limit);
            if (!geoapifyPlaces.isEmpty()) {
                places.addAll(geoapifyPlaces);
                sources.add("Geoapify Places API");
            }
        }

        if (places.size() < limit && useOpenTripMap()) {
            List<PlaceSuggestion> openTripMapPlaces = openTripMapPlaces(destination, vibe, limit - places.size());
            if (!openTripMapPlaces.isEmpty()) {
                places.addAll(openTripMapPlaces);
                sources.add("OpenTripMap API");
            }
        }

        List<PlaceSuggestion> ranked = rankAndDedupe(places, destination, vibe, limit);
        if (shouldUseAreaFallback(ranked, destination, vibe)) {
            return new PlaceDiscovery(
                    areaFallback(destination, vibe, limit),
                    "area-based planning fallback",
                    true
            );
        }

        return new PlaceDiscovery(ranked, sources.isEmpty() ? "template fallback" : String.join(" + ", sources), false);
    }

    private boolean useGeoapify() {
        return hasKey(geoapifyApiKey) && (provider.equalsIgnoreCase("hybrid") || provider.equalsIgnoreCase("geoapify"));
    }

    private boolean useOpenTripMap() {
        return hasKey(openTripMapApiKey) && (provider.equalsIgnoreCase("hybrid") || provider.equalsIgnoreCase("opentripmap"));
    }

    private List<PlaceSuggestion> geoapifyPlaces(String destination, String vibe, int limit) {
        try {
            Optional<Coordinates> coordinates = geoapifyCoordinates(destination);
            if (coordinates.isEmpty()) {
                return List.of();
            }

            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.geoapify.com")
                            .path("/v2/places")
                            .queryParam("categories", categoriesFor(vibe))
                            .queryParam("filter", "circle:" + coordinates.get().lon() + "," + coordinates.get().lat() + "," + SEARCH_RADIUS_METERS)
                            .queryParam("bias", "proximity:" + coordinates.get().lon() + "," + coordinates.get().lat())
                            .queryParam("limit", limit)
                            .queryParam("apiKey", geoapifyApiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("features") instanceof List<?> features)) {
                return List.of();
            }

            return features.stream()
                    .map(this::geoapifyPlace)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private Optional<Coordinates> geoapifyCoordinates(String destination) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.geoapify.com")
                            .path("/v1/geocode/search")
                            .queryParam("text", destination)
                            .queryParam("limit", 1)
                            .queryParam("apiKey", geoapifyApiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("features") instanceof List<?> features) || features.isEmpty()) {
                return Optional.empty();
            }
            if (!(features.getFirst() instanceof Map<?, ?> feature) || !(feature.get("geometry") instanceof Map<?, ?> geometry)) {
                return Optional.empty();
            }
            Object coordinates = geometry.get("coordinates");
            if (!(coordinates instanceof List<?> values) || values.size() < 2) {
                return Optional.empty();
            }
            if (values.get(0) instanceof Number lon && values.get(1) instanceof Number lat) {
                return Optional.of(new Coordinates(lat.doubleValue(), lon.doubleValue()));
            }
            return Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<PlaceSuggestion> geoapifyPlace(Object featureValue) {
        if (!(featureValue instanceof Map<?, ?> feature) || !(feature.get("properties") instanceof Map<?, ?> properties)) {
            return Optional.empty();
        }

        String name = firstText(properties.get("name"));
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Object categoryValue = properties.get("categories");
        String category = categoryValue == null ? "point of interest" : String.valueOf(categoryValue);
        String address = firstText(properties.get("address_line2"), properties.get("formatted"));
        return Optional.of(new PlaceSuggestion(name, category, address, "Geoapify"));
    }

    private List<PlaceSuggestion> openTripMapPlaces(String destination, String vibe, int limit) {
        try {
            Optional<Coordinates> coordinates = preferredCoordinates(destination);
            if (coordinates.isEmpty()) {
                return List.of();
            }

            List<?> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.opentripmap.com")
                            .path("/0.1/en/places/radius")
                            .queryParam("radius", SEARCH_RADIUS_METERS)
                            .queryParam("lon", coordinates.get().lon())
                            .queryParam("lat", coordinates.get().lat())
                            .queryParam("kinds", openTripMapKinds(destination, vibe))
                            .queryParam("rate", 2)
                            .queryParam("format", "json")
                            .queryParam("limit", limit)
                            .queryParam("apikey", openTripMapApiKey)
                            .build())
                    .retrieve()
                    .body(List.class);

            if (response == null) {
                return List.of();
            }
            return response.stream()
                    .map(this::openTripMapPlace)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private Optional<Coordinates> preferredCoordinates(String destination) {
        if (hasKey(geoapifyApiKey)) {
            Optional<Coordinates> geoapifyResult = geoapifyCoordinates(destination);
            if (geoapifyResult.isPresent()) {
                return geoapifyResult;
            }
        }
        return openTripMapCoordinates(destination);
    }

    private Optional<Coordinates> openTripMapCoordinates(String destination) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.opentripmap.com")
                            .path("/0.1/en/places/geoname")
                            .queryParam("name", destination)
                            .queryParam("apikey", openTripMapApiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("lat") instanceof Number lat) || !(response.get("lon") instanceof Number lon)) {
                return Optional.empty();
            }
            return Optional.of(new Coordinates(lat.doubleValue(), lon.doubleValue()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<PlaceSuggestion> openTripMapPlace(Object value) {
        if (!(value instanceof Map<?, ?> item)) {
            return Optional.empty();
        }
        String name = firstText(item.get("name"));
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String category = firstText(item.get("kinds"));
        return Optional.of(new PlaceSuggestion(name, category == null ? "tourist attraction" : category, "", "OpenTripMap"));
    }

    private List<PlaceSuggestion> rankAndDedupe(List<PlaceSuggestion> places, String destination, String vibe, int limit) {
        List<PlaceSuggestion> ranked = places.stream()
                .sorted((left, right) -> Integer.compare(
                        scorePlace(right, destination, vibe),
                        scorePlace(left, destination, vibe)
                ))
                .toList();

        Set<String> seen = new LinkedHashSet<>();
        List<PlaceSuggestion> unique = new ArrayList<>();
        for (PlaceSuggestion place : ranked) {
            if (!isUsefulTravelPlace(place)) {
                continue;
            }
            String key = place.name().toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!key.isBlank() && seen.add(key)) {
                unique.add(place);
            }
            if (unique.size() >= limit) {
                break;
            }
        }
        return unique;
    }

    private int scorePlace(PlaceSuggestion place, String destination, String vibe) {
        String signals = ((destination == null ? "" : destination) + " " + (vibe == null ? "" : vibe)).toLowerCase();
        String name = place.name().toLowerCase();
        String category = place.category() == null ? "" : place.category().toLowerCase();
        String address = place.address() == null ? "" : place.address().toLowerCase();
        int score = 0;

        if ("Geoapify".equalsIgnoreCase(place.source())) {
            score += 8;
        }
        if (name.contains("beach") || category.contains("beach")) {
            score += 26;
        }
        if (name.contains("market") || name.contains("promenade") || name.contains("waterfront")) {
            score += 12;
        }
        if (category.contains("tourism") || category.contains("interesting_places") || category.contains("view_point")) {
            score += 8;
        }
        if (category.contains("natural") || category.contains("leisure") || category.contains("park")) {
            score += 10;
        }
        if (category.contains("museum") || category.contains("architecture") || category.contains("historic")) {
            score += 4;
        }

        if (signals.contains("beach") || signals.contains("coast") || signals.contains("island") || signals.contains("goa")
                || signals.contains("bali") || signals.contains("phuket") || signals.contains("maldives")) {
            if (name.contains("beach") || category.contains("beach") || category.contains("natural") || category.contains("resort") || category.contains("view_point")) {
                score += 24;
            }
            if (name.contains("church") || name.contains("seminary") || name.contains("excavated") || category.contains("archaeolog")) {
                score -= 18;
            }
        }

        if (signals.contains("nightlife")) {
            if (category.contains("entertainment") || category.contains("bar") || name.contains("club") || name.contains("night")) {
                score += 22;
            }
            if (name.contains("quiet") || name.contains("memorial")) {
                score -= 10;
            }
        }

        if (signals.contains("family")) {
            if (category.contains("park") || category.contains("aquarium") || category.contains("zoo") || category.contains("leisure")) {
                score += 18;
            }
        }

        if (signals.contains("culture") || signals.contains("history") || signals.contains("heritage")) {
            if (category.contains("museum") || category.contains("historic") || category.contains("architecture") || name.contains("fort")) {
                score += 18;
            }
        }

        if (address.contains(destination == null ? "" : destination.toLowerCase())) {
            score += 6;
        }

        if (name.length() > 42) {
            score -= 4;
        }

        return score;
    }

    private boolean isUsefulTravelPlace(PlaceSuggestion place) {
        String name = place.name().toLowerCase();
        String category = place.category() == null ? "" : place.category().toLowerCase();
        if (name.length() < 4 || name.contains(",")) {
            return false;
        }
        if (name.contains("mcdonald") || name.contains("kfc") || name.contains("burger king") || name.equals("emarat")) {
            return false;
        }
        if (category.contains("fuel") || category.contains("fast_food") || category.contains("convenience")) {
            return false;
        }
        return true;
    }

    private boolean shouldUseAreaFallback(List<PlaceSuggestion> places, String destination, String vibe) {
        if (places.isEmpty()) {
            return true;
        }
        if (places.size() < 3) {
            return true;
        }

        int lowSignalCount = 0;
        Set<String> rootWords = new LinkedHashSet<>();
        for (PlaceSuggestion place : places) {
            String name = place.name().toLowerCase();
            String category = place.category() == null ? "" : place.category().toLowerCase();
            rootWords.add(primaryToken(name));

            if (name.contains("crypt")
                    || name.contains("crypte")
                    || name.contains("archaeolog")
                    || name.contains("fontaine")
                    || name.contains("parvis")
                    || name.contains("point zero")
                    || name.contains("point zéro")
                    || category.contains("archaeolog")) {
                lowSignalCount++;
            }
        }

        String signals = ((destination == null ? "" : destination) + " " + (vibe == null ? "" : vibe)).toLowerCase();
        boolean coastalTrip = signals.contains("goa")
                || signals.contains("beach")
                || signals.contains("coast")
                || signals.contains("island")
                || signals.contains("bali")
                || signals.contains("phuket")
                || signals.contains("maldives");
        if (coastalTrip) {
            long coastalMatches = places.stream()
                    .filter(place -> {
                        String name = place.name().toLowerCase();
                        String category = place.category() == null ? "" : place.category().toLowerCase();
                        return name.contains("beach")
                                || name.contains("coast")
                                || name.contains("market")
                                || name.contains("water")
                                || category.contains("beach")
                                || category.contains("natural")
                                || category.contains("resort")
                                || category.contains("view");
                    })
                    .count();
            if (coastalMatches < Math.min(2, places.size())) {
                return true;
            }
        }
        boolean cityTrip = !(signals.contains("beach") || signals.contains("island") || signals.contains("coast") || signals.contains("nature"));
        if (cityTrip && rootWords.size() <= Math.max(2, places.size() / 2)) {
            return true;
        }
        return lowSignalCount >= Math.max(2, places.size() / 2);
    }

    private String primaryToken(String name) {
        String[] parts = name.split("\\s+");
        for (String part : parts) {
            String cleaned = part.replaceAll("[^a-z]", "");
            if (cleaned.length() >= 4 && !List.of("place", "point", "saint", "sainte", "jardin").contains(cleaned)) {
                return cleaned;
            }
        }
        return name.replaceAll("[^a-z]", "");
    }

    private List<PlaceSuggestion> areaFallback(String destination, String vibe, int limit) {
        List<String> areas = areaSuggestions(destination, vibe);
        List<PlaceSuggestion> result = new ArrayList<>();
        for (String area : areas) {
            result.add(new PlaceSuggestion(area, "area-based city zone", destination, "TripMind Area Planner"));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<String> areaSuggestions(String destination, String vibe) {
        String signals = ((destination == null ? "" : destination) + " " + (vibe == null ? "" : vibe)).toLowerCase();

        if (signals.contains("paris")) {
            return List.of("Eiffel Tower and Trocadero area", "Louvre and central Paris", "Montmartre and Sacre-Coeur area", "Latin Quarter and Seine walk", "Le Marais neighborhood");
        }
        if (signals.contains("london")) {
            return List.of("Westminster and South Bank", "Covent Garden and Soho", "Tower Bridge and the City", "Notting Hill and Kensington", "Camden and Regent's Canal");
        }
        if (signals.contains("dubai")) {
            return List.of("Downtown Dubai and Burj area", "Dubai Marina and JBR", "Old Dubai and creek district", "Palm and beachfront zone", "Mall and indoor entertainment district");
        }
        if (signals.contains("goa")) {
            return List.of("North Goa beach belt", "Candolim and Calangute area", "Baga and nightlife strip", "Old Goa heritage side", "South Goa relaxed coast");
        }
        if (signals.contains("bangkok")) {
            return List.of("Riverside and old city temples", "Siam shopping district", "Sukhumvit nightlife zone", "Chatuchak and local market area", "Chao Phraya cultural belt");
        }
        if (signals.contains("beach") || signals.contains("coast") || signals.contains("island")) {
            return List.of("main beachfront area", "waterfront sunset stretch", "central leisure district", "local food and evening area", "scenic coastal viewpoint zone");
        }
        if (signals.contains("nightlife")) {
            return List.of("central landmark district", "food and evening area", "nightlife-friendly neighborhood", "shopping and walkable center", "relaxed local quarter");
        }
        return List.of("main landmark district", "central cultural area", "walkable local neighborhood", "food and evening area", "flexible city exploration zone");
    }

    private String categoriesFor(String vibe) {
        String lower = vibe == null ? "" : vibe.toLowerCase();
        if (lower.contains("nightlife")) {
            return "tourism.sights,entertainment,catering.bar,leisure";
        }
        if (lower.contains("family")) {
            return "tourism.sights,entertainment,leisure,natural";
        }
        if (lower.contains("beach") || lower.contains("nature") || lower.contains("relax")) {
            return "natural,leisure,tourism.sights";
        }
        return "tourism.sights,leisure,natural";
    }

    private String openTripMapKinds(String destination, String vibe) {
        String signals = ((destination == null ? "" : destination) + " " + (vibe == null ? "" : vibe)).toLowerCase();
        if (signals.contains("beach") || signals.contains("island") || signals.contains("coast") || signals.contains("goa") || signals.contains("bali") || signals.contains("phuket")) {
            return "beaches,natural,interesting_places,resorts,view_points,architecture,cultural";
        }
        if (signals.contains("nightlife")) {
            return "interesting_places,view_points,architecture,cultural";
        }
        return "interesting_places,natural,architecture,cultural,museums,view_points";
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }

    private record Coordinates(double lat, double lon) {
    }

    public record PlaceDiscovery(List<PlaceSuggestion> places, String source, boolean areaBased) {
        public static PlaceDiscovery empty() {
            return new PlaceDiscovery(List.of(), "template fallback", true);
        }
    }

    public record PlaceSuggestion(String name, String category, String address, String source) {
    }
}
