package com.tripmind.tripmind.service;

import com.tripmind.tripmind.dto.ChatRequest;
import com.tripmind.tripmind.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "tripmind.ai.provider=rule_based",
        "tripmind.places.provider=disabled"
})
@Import(ChatServiceTests.FixedClockConfig.class)
class ChatServiceTests {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private ChatService chatService;

    @Test
    void totalBudgetIsNotMultipliedAndCostRequestsPreserveSpendingLimit() {
        String sessionId = UUID.randomUUID().toString();
        ChatResponse first = chatService.handleChat(new ChatRequest(sessionId,
                "Plan Goa from Mumbai for 2 people, total budget INR 50000, round trip 2027-02-10 to 2027-02-14"));
        assertThat(first.tripSnapshot()).containsEntry("budget", 50000).containsEntry("budgetScope", "total");
        ChatResponse cheaper = chatService.handleChat(new ChatRequest(sessionId, "make it cheaper"));
        assertThat(cheaper.tripSnapshot()).containsEntry("budget", 50000).containsEntry("vibe", "budget");
        ChatResponse luxury = chatService.handleChat(new ChatRequest(sessionId, "more luxury"));
        assertThat(luxury.tripSnapshot()).containsEntry("budget", 50000);
    }

    @Test
    void asksForTripTypeBeforePlanningInsteadOfAssumingRoundTrip() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "plan goa from mumbai for 2 people with budget ₹50000 from 10 to 14 may"
        ));

        assertThat(response.readyToPlan()).isFalse();
        assertThat(response.plan()).isNull();
        assertThat(response.missingFields()).containsExactly("tripType");
        assertThat(response.response().toLowerCase()).contains("round trip").contains("one-way");
    }

    @Test
    void generatesPlanAfterTripTypeAndBudgetScopeAreConfirmed() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want to go dubai from mumbai"));
        chatService.handleChat(new ChatRequest(sessionId, "we are 2 people and our budget is 150000 from 20 to 26 may"));
        ChatResponse tripTypeReply = chatService.handleChat(new ChatRequest(sessionId, "round trip"));
        ChatResponse confirmReply = chatService.handleChat(new ChatRequest(sessionId, "total"));
        ChatResponse finalReply = chatService.handleChat(new ChatRequest(sessionId, "generate plan"));

        assertThat(tripTypeReply.readyToPlan()).isFalse();
        assertThat(tripTypeReply.missingFields()).containsExactly("budgetScope");
        assertThat(confirmReply.readyToPlan()).isTrue();
        assertThat(confirmReply.plan()).isNull();
        assertThat(confirmReply.conversationState()).isEqualTo("confirming");
        assertThat(finalReply.readyToPlan()).isTrue();
        assertThat(finalReply.plan()).isNotNull();
        assertThat(finalReply.tripSnapshot())
                .containsEntry("tripType", "round_trip")
                .containsEntry("budgetScope", "total")
                .containsEntry("destination", "Dubai")
                .containsEntry("origin", "Mumbai");
    }

    @Test
    void capturesPerPersonUsdBudgetAndNormalizesIt() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "trip to paris from mumbai for 2 people"));
        chatService.handleChat(new ChatRequest(sessionId, "round trip from 10 to 16 may"));
        ChatResponse confirmResponse = chatService.handleChat(new ChatRequest(sessionId, "budget is 1000 usd per person"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "generate plan"));

        assertThat(confirmResponse.readyToPlan()).isTrue();
        assertThat(confirmResponse.plan()).isNull();
        assertThat(confirmResponse.conversationState()).isEqualTo("confirming");
        assertThat(response.plan()).isNotNull();
        assertThat(response.tripSnapshot())
                .containsEntry("budgetCurrency", "USD")
                .containsEntry("budgetScope", "per_person");
        assertThat((Integer) response.tripSnapshot().get("budget")).isGreaterThan(1000);
        assertThat(response.plan().riskFlags().stream().map(risk -> risk.code()))
                .contains("PER_PERSON_BUDGET", "CURRENCY_NORMALIZED");
    }

    @Test
    void asksForExactDatesWhenUserUsesAmbiguousDateLanguage() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "plan kerala from mumbai for 2 people round trip with budget 60000 next month"
        ));

        assertThat(response.readyToPlan()).isFalse();
        assertThat(response.missingFields()).containsExactly("exactDates");
        assertThat(response.response().toLowerCase()).contains("exact departure and return dates");
    }

    @Test
    void asksForDestinationConfirmationWhenItLooksLikeATypo() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "plan barchelona from mumbai round trip for 2 people with budget 200000 from 10 to 16 may"
        ));

        assertThat(response.readyToPlan()).isFalse();
        assertThat(response.missingFields()).containsExactly("destinationConfirmation");
        assertThat(response.response()).contains("Barcelona");
    }

    @Test
    void capturesSpecialTravelerNeedsFromNaturalSentence() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "plan dubai from mumbai round trip from 10 to 15 may"));
        chatService.handleChat(new ChatRequest(sessionId, "we are 2 people and total budget is 120000"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "need halal food and wheelchair access"));

        assertThat(response.tripSnapshot().get("travelerNeeds").toString()).contains("halal", "wheelchair");
    }

    @Test
    void resetsSessionCleanlyWhenUserStartsANewTrip() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "plan goa from mumbai round trip for 2 people with budget 50000 from 10 to 14 may"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "new trip"));

        assertThat(response.tripSnapshot()).doesNotContainKeys("origin", "destination", "travelers", "budget", "departureDate", "returnDate");
        assertThat(response.readyToPlan()).isFalse();
    }

    @Test
    void ignoresSmallTalkAndDoesNotTreatTripKeywordsAsPlaces() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse greeting = chatService.handleChat(new ChatRequest(sessionId, "heeyy"));
        ChatResponse tripType = chatService.handleChat(new ChatRequest(sessionId, "round trip"));
        ChatResponse destination = chatService.handleChat(new ChatRequest(sessionId, "i want to go paris"));
        ChatResponse origin = chatService.handleChat(new ChatRequest(sessionId, "from mumbai"));
        ChatResponse details = chatService.handleChat(new ChatRequest(sessionId, "we are 2 and our budget is 250000"));
        ChatResponse dates = chatService.handleChat(new ChatRequest(sessionId, "from 3 to 7 may"));
        ChatResponse ready = chatService.handleChat(new ChatRequest(sessionId, "for both"));
        ChatResponse generate = chatService.handleChat(new ChatRequest(sessionId, "plan it"));

        assertThat(greeting.tripSnapshot()).doesNotContainKeys("destination", "origin");
        assertThat(tripType.tripSnapshot().get("tripType")).isEqualTo("round_trip");
        assertThat(tripType.tripSnapshot()).doesNotContainKeys("origin");
        assertThat(destination.tripSnapshot()).containsEntry("destination", "Paris");
        assertThat(origin.tripSnapshot()).containsEntry("origin", "Mumbai");
        assertThat(details.tripSnapshot()).containsEntry("destination", "Paris");
        assertThat(dates.tripSnapshot())
                .containsEntry("destination", "Paris")
                .containsEntry("travelers", 2)
                .containsEntry("budget", 250000);
        assertThat(ready.readyToPlan()).isTrue();
        assertThat(ready.conversationState()).isEqualTo("confirming");
        assertThat(generate.plan()).isNotNull();
        assertThat(generate.tripSnapshot())
                .containsEntry("destination", "Paris")
                .containsEntry("tripType", "round_trip");
        assertThat(generate.tripSnapshot()).doesNotContainEntry("origin", "Round Trip");
        assertThat(generate.plan().destination()).isEqualTo("Paris");
    }

    @Test
    void answersExploratoryQuestionWithoutStartingPlanningFlow() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "how much money will it take to paris?"
        ));

        assertThat(response.plan()).isNull();
        assertThat(response.tripSnapshot()).doesNotContainKeys("destination", "origin", "travelers", "budget");
        assertThat(response.response().toLowerCase()).contains("plan");
        assertThat(response.missingFields()).contains("origin", "destination", "travelers", "budget", "tripType", "departureDate", "returnDate");
    }

    @Test
    void canReturnFromExploratoryQuestionBackIntoPlanningFlow() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "how much money will it take to paris?"));
        ChatResponse planning = chatService.handleChat(new ChatRequest(
                sessionId,
                "i want to go paris from mumbai for 2 people with budget 250000 round trip from 3 to 7 may"
        ));

        assertThat(planning.tripSnapshot())
                .containsEntry("destination", "Paris")
                .containsEntry("origin", "Mumbai")
                .containsEntry("travelers", 2)
                .containsEntry("budget", 250000)
                .containsEntry("tripType", "round_trip");
    }

    @Test
    void asksForCityWhenUserGivesOnlyABroadCountryDestination() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "i want to go china"));

        assertThat(response.plan()).isNull();
        assertThat(response.readyToPlan()).isFalse();
        assertThat(response.missingFields()).containsExactly("destinationCity");
        assertThat(response.response()).contains("pick a city first");
    }

    @Test
    void broadDestinationClarificationAcceptsSpecificCityReply() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want to go china"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "beijing"));

        assertThat(response.tripSnapshot()).containsEntry("destination", "Beijing");
        assertThat(response.response().toLowerCase()).contains("round trip").contains("one-way");
    }

    @Test
    void suggestionStyleQuestionStaysExploratoryAndDoesNotAskTripType() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "heyy"));
        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "can you suggest some places to go because here in mumbai its summer season i want to get somewhere which is not hot and cool place"
        ));

        assertThat(response.plan()).isNull();
        assertThat(response.response().toLowerCase()).doesNotContain("round trip").doesNotContain("one-way");
        assertThat(response.response()).contains("Kathmandu").contains("Kashmir");
        assertThat(response.tripSnapshot()).doesNotContainKeys("destination", "tripType");
    }

    @Test
    void roughBudgetQuestionGetsEstimateWithoutStartingPlanning() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "how much money will it take to paris?"
        ));

        assertThat(response.plan()).isNull();
        assertThat(response.response()).contains("₹").contains("Paris");
        assertThat(response.tripSnapshot()).doesNotContainKeys("destination", "budget", "tripType");
    }

    @Test
    void mixedIntentSuggestionWithBudgetStaysExploratoryButUseful() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "suggest me something cool and if it fits in 2 lakh then we can plan it"
        ));

        assertThat(response.plan()).isNull();
        assertThat(response.response()).contains("Kashmir").contains("Kathmandu");
        assertThat(response.response().toLowerCase()).doesNotContain("round trip").doesNotContain("one-way");
        assertThat(response.tripSnapshot()).doesNotContainKeys("destination", "tripType");
    }

    @Test
    void misspelledNewZealandGetsNormalizedAndAskedAsBroadDestination() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "okay lets plan a trip fpr new zeland"
        ));

        assertThat(response.plan()).isNull();
        assertThat(response.missingFields()).containsExactly("destinationCity");
        assertThat(response.response()).contains("New Zealand").contains("Auckland");
    }

    @Test
    void acceptsRoundAsRoundTripClarification() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want to go paris"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "round"));

        assertThat(response.tripSnapshot()).containsEntry("tripType", "round_trip");
    }

    @Test
    void capturesOriginAndDatesFromCombinedFromMessage() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want to go paris"));
        chatService.handleChat(new ChatRequest(sessionId, "round trip"));
        chatService.handleChat(new ChatRequest(sessionId, "we are 2 people and our budget is 250000"));
        chatService.handleChat(new ChatRequest(sessionId, "for group"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "from mumbai from 2 to 6 may"));

        assertThat(response.tripSnapshot())
                .containsEntry("origin", "Mumbai")
                .containsEntry("departureDate", "2026-05-02")
                .containsEntry("returnDate", "2026-05-06");
    }

    @Test
    void originMessageDoesNotOverwriteDestinationAndExplicitDestinationCorrectionWins() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "plan me a trip for ireland"));
        chatService.handleChat(new ChatRequest(sessionId, "i am from mumbai we are 3 traveler and our budget is 450000"));
        chatService.handleChat(new ChatRequest(sessionId, "round trip"));
        chatService.handleChat(new ChatRequest(sessionId, "for the group"));
        ChatResponse datesReply = chatService.handleChat(new ChatRequest(sessionId, "from mumbai from 5 to 10 may"));
        ChatResponse destinationCorrection = chatService.handleChat(new ChatRequest(sessionId, "destination is ireland"));
        ChatResponse readyReply = chatService.handleChat(new ChatRequest(sessionId, "yes"));

        assertThat(datesReply.tripSnapshot())
                .containsEntry("origin", "Mumbai")
                .containsEntry("destination", "Ireland");
        assertThat(destinationCorrection.tripSnapshot())
                .containsEntry("origin", "Mumbai")
                .containsEntry("destination", "Ireland");
        assertThat(readyReply.tripSnapshot())
                .containsEntry("origin", "Mumbai")
                .containsEntry("destination", "Ireland");
        assertThat(readyReply.response()).contains("Mumbai to Ireland");
    }

    @Test
    void vagueAsianCoolPlaceRequestStaysInSuggestionMode() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "i want to go some asian place but it should be cool during next month or upcoming 20 days"
        ));

        assertThat(response.plan()).isNull();
        assertThat(response.response()).contains("Kathmandu").contains("Kashmir");
        assertThat(response.response().toLowerCase()).doesNotContain("departure and return dates");
        assertThat(response.tripSnapshot()).doesNotContainKeys("destination", "tripType");
    }

    @Test
    void followUpWhichPlaceQuestionStillGetsSuggestions() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want to go some asian place but it should be cool during next month or upcoming 20 days"));
        chatService.handleChat(new ChatRequest(sessionId, "from 3 to 7 may"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "but whihc place is you didnt suggested anything"));

        assertThat(response.plan()).isNull();
        assertThat(response.response()).contains("Kathmandu").contains("Leh");
    }

    @Test
    void datesBeforeDestinationKeepDestinationDiscoveryMode() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want somewhere cool in asia"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "from 3 to 7 may"));

        assertThat(response.plan()).isNull();
        assertThat(response.response().toLowerCase()).contains("still choosing the destination");
        assertThat(response.tripSnapshot())
                .containsEntry("departureDate", "2026-05-03")
                .containsEntry("returnDate", "2026-05-07");
        assertThat(response.tripSnapshot()).doesNotContainKey("destination");
    }

    @Test
    void genericSomewhereCoolPhraseDoesNotBecomeDestination() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "from mumbai to somewhere cool for 2 people with budget 90000"
        ));

        assertThat(response.plan()).isNull();
        assertThat(response.tripSnapshot()).containsEntry("origin", "Mumbai");
        assertThat(response.tripSnapshot()).doesNotContainKey("destination");
        assertThat(response.response().toLowerCase()).contains("still choosing the destination");
    }

    @Test
    void correctionMessageUpdatesTravelerCountWithoutRegeneratingImmediately() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "plan paris from mumbai round trip for 2 people with total budget 250000 from 3 to 7 may"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "actually make that 3 people"));

        assertThat(response.plan()).isNull();
        assertThat(response.tripSnapshot()).containsEntry("travelers", 3);
        assertThat(response.response().toLowerCase()).contains("updated").contains("generate plan");
    }

    @Test
    void broadDestinationDoesNotAcceptBareYesAsChosenCity() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want to go italy"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "yes"));

        assertThat(response.plan()).isNull();
        assertThat(response.missingFields()).containsExactly("destinationCity");
        assertThat(response.response()).contains("pick a city first");
        assertThat(response.tripSnapshot()).doesNotContainKey("destination");
    }

    @Test
    void companionPhraseCountsAsTwoTravelers() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "i want to go paris from mumbai with me and my wife round trip from 3 to 7 may"
        ));

        assertThat(response.tripSnapshot()).containsEntry("travelers", 2);
    }

    @Test
    void visaQuestionStaysExploratoryAndDoesNotLockDestinationIntoSession() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(
                sessionId,
                "do i need visa for paris?"
        ));

        assertThat(response.plan()).isNull();
        assertThat(response.tripSnapshot()).doesNotContainKey("destination");
        assertThat(response.response().toLowerCase()).contains("plan");
    }

    @Test
    void exploratoryFollowUpAboutBoraBoraKeepsContextInsteadOfJumpingToPlanning() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want to go some romantic place"));
        chatService.handleChat(new ChatRequest(sessionId, "what is bora bora"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "in which country"));

        assertThat(response.plan()).isNull();
        assertThat(response.response()).contains("French Polynesia").contains("France");
        assertThat(response.response().toLowerCase()).doesNotContain("to plan your trip");
        assertThat(response.tripSnapshot()).doesNotContainKey("destination");
    }

    @Test
    void franceNeedsCityClarificationBeforePlanning() {
        String sessionId = UUID.randomUUID().toString();

        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "i want to go france"));

        assertThat(response.plan()).isNull();
        assertThat(response.missingFields()).containsExactly("destinationCity");
        assertThat(response.response()).contains("France").contains("Paris");
        assertThat(response.tripSnapshot()).doesNotContainKey("destination");
    }

    @Test
    void franceClarificationUsesRomanticContextToSuggestParisFirst() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want to go some romantic place"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "i want to go france"));

        assertThat(response.plan()).isNull();
        assertThat(response.response()).contains("romantic").contains("Paris").contains("Nice").contains("Lyon");
        assertThat(response.missingFields()).containsExactly("destinationCity");
    }

    @Test
    void newZealandClarificationUsesAdventureContextToSuggestQueenstown() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want an adventure trip"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "i want to go new zealand"));

        assertThat(response.plan()).isNull();
        assertThat(response.response()).contains("adventure").contains("Queenstown").contains("Auckland").contains("Wellington");
        assertThat(response.missingFields()).containsExactly("destinationCity");
    }

    @Test
    void thailandClarificationUsesNightlifeContextToSuggestBangkok() {
        String sessionId = UUID.randomUUID().toString();

        chatService.handleChat(new ChatRequest(sessionId, "i want a nightlife trip"));
        ChatResponse response = chatService.handleChat(new ChatRequest(sessionId, "i want to go thailand"));

        assertThat(response.plan()).isNull();
        assertThat(response.response()).contains("nightlife").contains("Bangkok").contains("Phuket").contains("Chiang Mai");
        assertThat(response.missingFields()).containsExactly("destinationCity");
    }
}
