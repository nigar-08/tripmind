package com.tripmind.tripmind.service.provider;

import com.tripmind.tripmind.model.TripRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenWeatherService {

    private final RestClient restClient;
    private final String apiKey;
    private final String provider;

    public OpenWeatherService(
            RestClient.Builder restClientBuilder,
            @Value("${tripmind.weather.openweather.api-key:${OPENWEATHER_API_KEY:}}") String apiKey,
            @Value("${tripmind.weather.provider:openweather}") String provider
    ) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.provider = provider;
    }

    public Optional<WeatherSignal> forecast(TripRequest request) {
        if (!"openweather".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            Optional<Coordinates> coordinates = coordinatesFor(request.destination());
            if (coordinates.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.openweathermap.org")
                            .path("/data/2.5/forecast")
                            .queryParam("lat", coordinates.get().lat())
                            .queryParam("lon", coordinates.get().lon())
                            .queryParam("appid", apiKey)
                            .queryParam("units", "metric")
                            .build())
                    .retrieve()
                    .body(Map.class);

            return buildSignal(request, response);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Coordinates> coordinatesFor(String destination) {
        List<?> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.openweathermap.org")
                        .path("/geo/1.0/direct")
                        .queryParam("q", destination)
                        .queryParam("limit", 1)
                        .queryParam("appid", apiKey)
                        .build())
                .retrieve()
                .body(List.class);

        if (response == null || response.isEmpty() || !(response.getFirst() instanceof Map<?, ?> location)) {
            return Optional.empty();
        }

        Object lat = location.get("lat");
        Object lon = location.get("lon");
        if (!(lat instanceof Number latNumber) || !(lon instanceof Number lonNumber)) {
            return Optional.empty();
        }
        return Optional.of(new Coordinates(latNumber.doubleValue(), lonNumber.doubleValue()));
    }

    private Optional<WeatherSignal> buildSignal(TripRequest request, Map<String, Object> response) {
        if (response == null || !(response.get("list") instanceof List<?> entries) || entries.isEmpty()) {
            return Optional.empty();
        }

        LocalDate departure = LocalDate.parse(request.departureDate());
        LocalDate returnDate = LocalDate.parse(request.returnDate());
        LocalDate latestForecastDate = latestForecastDate(entries);
        boolean tripOutsideForecastWindow = latestForecastDate != null && departure.isAfter(latestForecastDate);

        List<Map<?, ?>> relevantEntries = entries.stream()
                .map(this::asMap)
                .flatMap(Optional::stream)
                .filter(entry -> isRelevantForecastEntry(entry, departure, returnDate, tripOutsideForecastWindow))
                .toList();

        if (relevantEntries.isEmpty()) {
            relevantEntries = entries.stream()
                    .map(this::asMap)
                    .flatMap(Optional::stream)
                    .toList();
        }

        DoubleSummaryStatistics temperatures = relevantEntries.stream()
                .map(this::temperatureFrom)
                .flatMap(Optional::stream)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        long rainySlots = relevantEntries.stream()
                .filter(this::isRainy)
                .count();

        String seasonSignal = signalFrom(temperatures, rainySlots, relevantEntries.size());
        String summary = summaryFrom(request.destination(), temperatures, rainySlots, relevantEntries.size(), tripOutsideForecastWindow);
        return Optional.of(new WeatherSignal(summary, seasonSignal, "OpenWeather 5 Day / 3 Hour Forecast API"));
    }

    private LocalDate latestForecastDate(List<?> entries) {
        return entries.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(entry -> entry.get("dt_txt"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(this::forecastDate)
                .flatMap(Optional::stream)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private Optional<Map<?, ?>> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return Optional.of(map);
        }
        return Optional.empty();
    }

    private boolean isRelevantForecastEntry(Map<?, ?> entry, LocalDate departure, LocalDate returnDate, boolean tripOutsideForecastWindow) {
        Optional<LocalDate> forecastDate = forecastDate(entry.get("dt_txt"));
        if (forecastDate.isEmpty()) {
            return false;
        }
        if (tripOutsideForecastWindow) {
            return true;
        }
        return !forecastDate.get().isBefore(departure) && forecastDate.get().isBefore(returnDate.plusDays(1));
    }

    private Optional<LocalDate> forecastDate(Object value) {
        try {
            if (value instanceof String text) {
                return Optional.of(OffsetDateTime.parse(text.replace(" ", "T") + "Z").withOffsetSameInstant(ZoneOffset.UTC).toLocalDate());
            }
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Double> temperatureFrom(Map<?, ?> entry) {
        Object main = entry.get("main");
        if (main instanceof Map<?, ?> mainMap && mainMap.get("temp") instanceof Number temperature) {
            return Optional.of(temperature.doubleValue());
        }
        return Optional.empty();
    }

    private boolean isRainy(Map<?, ?> entry) {
        Object weather = entry.get("weather");
        if (!(weather instanceof List<?> weatherItems)) {
            return false;
        }
        return weatherItems.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> String.valueOf(item.get("main")).toLowerCase(Locale.ENGLISH))
                .anyMatch(main -> main.contains("rain") || main.contains("thunderstorm") || main.contains("drizzle"));
    }

    private String signalFrom(DoubleSummaryStatistics temperatures, long rainySlots, int totalSlots) {
        double averageTemp = temperatures.getCount() == 0 ? 0 : temperatures.getAverage();
        double rainShare = totalSlots == 0 ? 0 : (double) rainySlots / totalSlots;
        if (rainShare >= 0.3) {
            return "rain-risk";
        }
        if (averageTemp >= 34) {
            return "hot-season";
        }
        if (averageTemp > 0) {
            return "live-forecast";
        }
        return "forecast-unavailable";
    }

    private String summaryFrom(
            String destination,
            DoubleSummaryStatistics temperatures,
            long rainySlots,
            int totalSlots,
            boolean tripOutsideForecastWindow
    ) {
        if (temperatures.getCount() == 0) {
            return "OpenWeather returned forecast context, but temperature details were not available.";
        }

        String timingNote = tripOutsideForecastWindow
                ? " Your trip is beyond the short-range forecast window, so this uses current 5-day destination weather as a planning signal."
                : "";
        return "OpenWeather live forecast for " + destination
                + " shows average temperature around " + Math.round(temperatures.getAverage())
                + "°C, with rain risk in " + rainySlots + " of " + totalSlots + " forecast slots."
                + timingNote;
    }

    private record Coordinates(double lat, double lon) {
    }

    public record WeatherSignal(String summary, String seasonSignal, String source) {
    }
}
