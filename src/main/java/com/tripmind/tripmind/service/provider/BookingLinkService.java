package com.tripmind.tripmind.service.provider;

import com.tripmind.tripmind.dto.BookingLinkView;
import com.tripmind.tripmind.model.TripRequest;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class BookingLinkService {

    public List<BookingLinkView> flightLinks(TripRequest request) {
        boolean hasCoreParams = hasText(request.origin()) && hasText(request.destination()) && hasText(request.departureDate());
        boolean isRoundTrip = !"one_way".equalsIgnoreCase(request.tripType());
        boolean hasReturn = hasText(request.returnDate());
        boolean enabled = hasCoreParams && (!isRoundTrip || hasReturn);
        String searchText = "flights from " + request.origin()
                + " to " + request.destination()
                + " departing " + request.departureDate()
                + (hasReturn ? " returning " + request.returnDate() : "")
                + " for " + request.travelers() + " travelers";

        return List.of(
                new BookingLinkView(
                        "Google Flights",
                        "Open live flight search",
                        enabled ? "https://www.google.com/travel/flights?q=" + encode(searchText) : "",
                        enabled ? "Live supplier prices open outside TripMind." : "Flight link is disabled until route, trip type, and required dates are fully confirmed.",
                        enabled
                ),
                new BookingLinkView(
                        "MakeMyTrip",
                        "Search on MakeMyTrip",
                        enabled ? makemyTripFlightUrl(request, isRoundTrip) : "",
                        enabled ? "Uses route, dates, passenger count, and economy cabin where supported." : "Flight link is disabled until TripMind has the required booking parameters.",
                        enabled
                ),
                new BookingLinkView(
                        "Skyscanner",
                        "Compare flight partners",
                        enabled ? skyscannerFlightUrl(request, isRoundTrip) : "",
                        enabled ? "Partner page may ask you to confirm airport codes before showing live fares." : "Flight link is disabled until TripMind has the required booking parameters.",
                        enabled
                )
        );
    }

    public List<BookingLinkView> hotelLinks(TripRequest request) {
        boolean enabled = hasText(request.destination()) && hasText(request.departureDate()) && hasText(request.returnDate());
        String searchText = "hotels in " + request.destination()
                + " check in " + request.departureDate()
                + " check out " + request.returnDate()
                + " for " + request.travelers() + " travelers";

        return List.of(
                new BookingLinkView(
                        "Google Hotels",
                        "Open hotel search",
                        enabled ? "https://www.google.com/travel/hotels?q=" + encode(searchText) : "",
                        enabled ? "Good for quickly checking current location, rating, and supplier prices." : "Hotel link is disabled until destination and stay dates are fully confirmed.",
                        enabled
                ),
                new BookingLinkView(
                        "Booking.com",
                        "Check hotel inventory",
                        enabled ? "https://www.booking.com/searchresults.html?ss=" + encode(request.destination())
                                + "&checkin=" + request.departureDate()
                                + "&checkout=" + request.returnDate()
                                + "&group_adults=" + request.travelers()
                                + "&no_rooms=" + Math.max(1, (request.travelers() + 1) / 2) : "",
                        enabled ? "Live inventory and final taxes must be confirmed on Booking.com." : "Hotel link is disabled until TripMind has the required booking parameters.",
                        enabled
                ),
                new BookingLinkView(
                        "MakeMyTrip Hotels",
                        "Search Indian travel partner",
                        enabled ? "https://www.makemytrip.com/hotels/hotel-listing/?searchText=" + encode(request.destination())
                                + "&checkin=" + request.departureDate()
                                + "&checkout=" + request.returnDate()
                                + "&roomStayQualifier=" + encode(Math.max(1, (request.travelers() + 1) / 2)
                                + "e" + request.travelers() + "e0e") : "",
                        enabled ? "Useful for India-based users comparing bundled travel options." : "Hotel link is disabled until TripMind has the required booking parameters.",
                        enabled
                )
        );
    }

    private String makemyTripFlightUrl(TripRequest request, boolean isRoundTrip) {
        if (isRoundTrip) {
            return "https://www.makemytrip.com/flights/?tripType=R&itinerary="
                    + encode(request.origin() + "-" + request.destination() + "-" + request.departureDate()
                    + "_" + request.destination() + "-" + request.origin() + "-" + request.returnDate())
                    + "&paxType=A-" + request.travelers() + "_C-0_I-0&cabinClass=E";
        }
        return "https://www.makemytrip.com/flights/?tripType=O&itinerary="
                + encode(request.origin() + "-" + request.destination() + "-" + request.departureDate())
                + "&paxType=A-" + request.travelers() + "_C-0_I-0&cabinClass=E";
    }

    private String skyscannerFlightUrl(TripRequest request, boolean isRoundTrip) {
        String base = "https://www.skyscanner.co.in/transport/flights/"
                + encode(pathToken(request.origin())) + "/"
                + encode(pathToken(request.destination())) + "/"
                + compactDate(request.departureDate()) + "/";
        if (isRoundTrip) {
            return base + compactDate(request.returnDate()) + "/?adults=" + request.travelers();
        }
        return base + "?adults=" + request.travelers();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String pathToken(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private String compactDate(String isoDate) {
        return isoDate == null ? "" : isoDate.replace("-", "").substring(2);
    }
}
