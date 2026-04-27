package com.tripmind.tripmind.service;

import com.tripmind.tripmind.model.TripRequest;
import org.springframework.stereotype.Service;

@Service
public class DestinationArchetypeService {

    public String classify(TripRequest request) {
        String signals = normalize(request);

        if (containsAny(signals, "beach", "coast", "island", "goa", "bali", "phuket", "maldives")) {
            return "coastal leisure trip";
        }
        if (containsAny(signals, "nightlife", "party", "club", "bangkok", "dubai")) {
            return "city nightlife trip";
        }
        if (containsAny(signals, "family", "kids", "parents", "elderly")) {
            return "family comfort trip";
        }
        if (containsAny(signals, "heritage", "history", "culture", "museum", "fort")) {
            return "culture and heritage trip";
        }
        if (containsAny(signals, "nature", "mountain", "snow", "trek", "kashmir", "switzerland", "iceland")) {
            return "nature and scenic trip";
        }
        if (containsAny(signals, "luxury", "honeymoon", "resort")) {
            return "premium escape";
        }
        return "general city-break trip";
    }

    public String travelerProfile(TripRequest request) {
        if (request.travelers() >= 5) {
            return "group planning";
        }
        if (request.travelers() == 4) {
            return "small group travel";
        }
        if (request.travelers() == 3) {
            return "3-traveler planning";
        }
        if (request.travelers() == 2) {
            if (containsAny(normalize(request), "honeymoon", "couple", "romantic")) {
                return "couple trip";
            }
            return "2-traveler planning";
        }
        return "solo planning";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(TripRequest request) {
        return ((request.destination() == null ? "" : request.destination()) + " "
                + (request.vibe() == null ? "" : request.vibe()) + " "
                + (request.specialRequest() == null ? "" : request.specialRequest()))
                .toLowerCase();
    }
}
