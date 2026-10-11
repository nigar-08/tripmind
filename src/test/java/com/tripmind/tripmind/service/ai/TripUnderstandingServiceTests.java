package com.tripmind.tripmind.service.ai;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Clock;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TripUnderstandingServiceTests {
    @Test
    void convertsModelJsonIntoValidatedTripFieldsAndFallsBackOnModelFailure() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            ChatModel model = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("""
                    {"origin":"Mumbai","destination":"Goa","travelers":2,"budget":50000,
                     "departureDate":"2027-02-10","returnDate":"2027-02-14","tripType":"round_trip",
                     "budgetCurrency":"INR","budgetScope":"total","vibe":"comfortable",
                     "specialRequest":"historical places","travelerNeeds":"vegetarian"}
                    """))));
            var beans = new StaticListableBeanFactory();
            beans.addBean("model", model);
            var service = new TripUnderstandingService(beans.getBeanProvider(ChatModel.class),
                    validatorFactory.getValidator(), Clock.systemUTC());
            var fields = service.extract("A Goa trip for two, vegetarian food", Map.of()).orElseThrow();
            assertThat(fields.destination()).isEqualTo("Goa");
            assertThat(fields.travelers()).isEqualTo(2);
            assertThat(fields.travelerNeeds()).isEqualTo("vegetarian");
            beans = new StaticListableBeanFactory();
            ChatModel failingModel = prompt -> { throw new IllegalStateException("unavailable"); };
            beans.addBean("model", failingModel);
            var offline = new TripUnderstandingService(beans.getBeanProvider(ChatModel.class),
                    validatorFactory.getValidator(), Clock.systemUTC());
            assertThat(offline.extract("A Goa trip", Map.of())).isEmpty();
        }
    }

    @Test
    void rejectsInvalidCalendarDatesAndUnsafeValuesButAcceptsPartialFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new TripUnderstandingService(new StaticListableBeanFactory().getBeanProvider(ChatModel.class),
                    factory.getValidator(), Clock.systemUTC());
            assertThat(service.extract("plan a trip", Map.of())).isEmpty();
            assertThat(service.validated(fields(2, "2027-02-30", "2027-03-04"))).isEmpty();
            assertThat(service.validated(fields(0, "2027-02-10", "2027-02-14"))).isEmpty();
            assertThat(service.validated(fields(2, "2027-02-14", "2027-02-10"))).isEmpty();
            assertThat(service.validated(fields(2, null, null))).isPresent();
        }
    }

    private TripUnderstandingService.TripFields fields(int travelers, String departure, String returning) {
        return new TripUnderstandingService.TripFields(null, "Goa", travelers, null,
                departure, returning, null, null, null, null, null, "vegetarian");
    }
}
