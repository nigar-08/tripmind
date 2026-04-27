package com.tripmind.tripmind.service.agent;

import com.tripmind.tripmind.model.AgentResult;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.provider.OpenWeatherService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class WeatherAgentService {

    private final OpenWeatherService openWeatherService;

    public WeatherAgentService(OpenWeatherService openWeatherService) {
        this.openWeatherService = openWeatherService;
    }

    public WeatherOutput forecast(TripRequest request) {
        if (ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(request.departureDate())) > 14) {
            String seasonSignal = seasonSignal(request);
            return new WeatherOutput(
                    "Live forecasts are unreliable this far ahead, so TripMind switched to seasonal averages for " + request.destination() + ". " + heuristicWeatherSummary(request, seasonSignal, false),
                    seasonSignal,
                    "historical seasonal average",
                    travelTimingNote(request, seasonSignal),
                    packingList(request, seasonSignal),
                    new AgentResult("weather-agent", "degraded", "Used seasonal weather guidance because the trip is beyond the reliable forecast window.", "weather and season risk", null, true)
            );
        }
        Optional<OpenWeatherService.WeatherSignal> liveForecast = openWeatherService.forecast(request);
        if (liveForecast.isPresent()) {
            OpenWeatherService.WeatherSignal signal = liveForecast.get();
            String summary = "Pulled live OpenWeather forecast context for " + request.destination()
                    + " between " + request.departureDate() + " and " + request.returnDate() + ".";
            return new WeatherOutput(
                    signal.summary(),
                    signal.seasonSignal(),
                    signal.source(),
                    travelTimingNote(request, signal.seasonSignal()),
                    packingList(request, signal.seasonSignal()),
                    new AgentResult("weather-agent", "completed", summary, "weather and season risk", null, false)
            );
        }

        String seasonSignal = seasonSignal(request);
        String weatherSummary = heuristicWeatherSummary(request, seasonSignal, false);
        String summary = "Prepared weather context for " + request.destination()
                + " between " + request.departureDate() + " and " + request.returnDate() + ".";
        return new WeatherOutput(
                weatherSummary,
                seasonSignal,
                "season-aware planning heuristic",
                travelTimingNote(request, seasonSignal),
                packingList(request, seasonSignal),
                new AgentResult("weather-agent", "completed", summary, "weather and season risk", null, false)
        );
    }

    public WeatherOutput fallback(TripRequest request, String reason) {
        String seasonSignal = seasonSignal(request);
        String summary = "Weather provider degraded; using season-aware planning context for "
                + request.destination() + ".";
        return new WeatherOutput(
                heuristicWeatherSummary(request, seasonSignal, true),
                seasonSignal,
                "degraded weather fallback",
                travelTimingNote(request, seasonSignal),
                packingList(request, seasonSignal),
                new AgentResult("weather-agent", "degraded", summary + " Reason: " + reason, "weather and season risk", null, true)
        );
    }

    private String travelTimingNote(TripRequest request, String seasonSignal) {
        return switch (seasonSignal) {
            case "rain-risk" -> "This travel window can be wetter than average, so expect rain interruptions and keep indoor backups ready.";
            case "hot-season" -> "This is a hotter travel window, so the trip works better with early starts and lighter afternoons.";
            case "cold-season" -> "This is a colder travel window, so outdoor blocks are better later in the day with layered clothing.";
            case "humid-season" -> "This period can feel sticky and slower, so the trip works best with hydration breaks and lighter afternoon plans.";
            default -> "This looks like a comfortable time to visit for a balanced mix of sightseeing and flexible exploration.";
        };
    }

    private String seasonSignal(TripRequest request) {
        String normalized = ((request.destination() == null ? "" : request.destination()) + " "
                + (request.vibe() == null ? "" : request.vibe()) + " "
                + (request.specialRequest() == null ? "" : request.specialRequest())).toLowerCase();
        Month month = LocalDate.parse(request.departureDate()).getMonth();

        if (isMonsoonProne(normalized) && isMonsoonMonth(month)) {
            return "rain-risk";
        }
        if (isDesertOrHot(normalized) && isWarmSeason(month)) {
            return "hot-season";
        }
        if (isColdSeasonDestination(normalized) && isWinterMonth(month)) {
            return "cold-season";
        }
        if (isTropical(normalized) && isShoulderHeatMonth(month)) {
            return "humid-season";
        }
        return "pleasant-season";
    }

    private String heuristicWeatherSummary(TripRequest request, String seasonSignal, boolean degraded) {
        String prefix = degraded
                ? "Live weather was unavailable, so TripMind switched to season-aware planning for "
                : "TripMind used season-aware planning context for ";
        String packing = packingAdvice(seasonSignal);

        return switch (seasonSignal) {
            case "hot-season" -> prefix + request.destination() + ": expect hotter daytime conditions, keep indoor breaks around afternoon hours, and avoid overpacking outdoor activities. Packing: " + packing;
            case "rain-risk" -> prefix + request.destination() + ": weather can turn quickly, so keep one indoor backup option, flexible transfers, and cancellation-friendly bookings. Packing: " + packing;
            case "cold-season" -> prefix + request.destination() + ": expect colder outdoor windows, slower morning starts, and layering needs for open-air sightseeing. Packing: " + packing;
            case "humid-season" -> prefix + request.destination() + ": plan lighter daytime activity, hydration breaks, and one indoor reset block in the afternoon. Packing: " + packing;
            default -> prefix + request.destination() + ": the travel window looks broadly comfortable for a balanced mix of sightseeing and flexible exploration. Packing: " + packing;
        };
    }

    private String packingAdvice(String seasonSignal) {
        return switch (seasonSignal) {
            case "hot-season" -> "light cotton layers, sunscreen, a cap, and extra water";
            case "rain-risk" -> "rain gear, quick-dry clothing, waterproof footwear, and a small umbrella";
            case "cold-season" -> "warm layers, a jacket, and comfortable covered shoes";
            case "humid-season" -> "breathable clothing, sunscreen, and hydration essentials";
            default -> "comfortable walking wear, light layers, and basic sun protection";
        };
    }

    private List<String> packingList(TripRequest request, String seasonSignal) {
        List<String> base = switch (seasonSignal) {
            case "hot-season" -> List.of("light cotton clothing", "sunscreen SPF 50", "cap or hat", "reusable water bottle");
            case "rain-risk" -> List.of("umbrella or poncho", "quick-dry clothing", "waterproof footwear", "small dry pouch");
            case "cold-season" -> List.of("light jacket", "layered tops", "comfortable covered shoes", "warm evening layer");
            case "humid-season" -> List.of("breathable clothes", "sunscreen", "hydration essentials", "extra change of clothes");
            default -> List.of("comfortable walking wear", "sun protection", "light layer", "day bag");
        };
        if (request.vibe() != null && request.vibe().toLowerCase().contains("beach")) {
            return appendIfMissing(base, "swimwear");
        }
        return base;
    }

    private List<String> appendIfMissing(List<String> base, String extra) {
        if (base.contains(extra)) {
            return base;
        }
        java.util.ArrayList<String> items = new java.util.ArrayList<>(base);
        items.add(extra);
        return items;
    }

    private boolean isMonsoonProne(String signals) {
        return signals.contains("goa")
                || signals.contains("kerala")
                || signals.contains("bali")
                || signals.contains("phuket")
                || signals.contains("coast")
                || signals.contains("beach")
                || signals.contains("island");
    }

    private boolean isDesertOrHot(String signals) {
        return signals.contains("dubai")
                || signals.contains("abu dhabi")
                || signals.contains("doha")
                || signals.contains("desert")
                || signals.contains("middle east");
    }

    private boolean isColdSeasonDestination(String signals) {
        return signals.contains("switzerland")
                || signals.contains("japan")
                || signals.contains("iceland")
                || signals.contains("europe")
                || signals.contains("kashmir")
                || signals.contains("mountain")
                || signals.contains("snow");
    }

    private boolean isTropical(String signals) {
        return signals.contains("bangkok")
                || signals.contains("singapore")
                || signals.contains("malaysia")
                || signals.contains("thailand")
                || signals.contains("tropical")
                || signals.contains("humid");
    }

    private boolean isMonsoonMonth(Month month) {
        return month.getValue() >= 6 && month.getValue() <= 9;
    }

    private boolean isWarmSeason(Month month) {
        return month.getValue() >= 4 && month.getValue() <= 9;
    }

    private boolean isWinterMonth(Month month) {
        return month.getValue() == 11 || month.getValue() == 12 || month.getValue() <= 2;
    }

    private boolean isShoulderHeatMonth(Month month) {
        return month.getValue() >= 3 && month.getValue() <= 6;
    }

    public record WeatherOutput(
            String summary,
            String seasonSignal,
            String source,
            String travelTiming,
            List<String> packingList,
            AgentResult agentResult
    ) {
    }
}
