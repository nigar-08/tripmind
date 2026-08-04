package com.tripmind.tripmind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "trip_sessions", indexes = {
        @Index(name = "idx_trip_session_updated", columnList = "updatedAt")
})
public class TripSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String sessionKey;

    @Column(unique = true)
    private String requestId;

    @Column(nullable = false)
    private String destination;

    private String origin;

    private Integer travelers;

    private Integer budget;

    private String departureDate;

    private String returnDate;

    private String tripType;

    private String budgetCurrency;

    private String budgetScope;

    private String vibe;

    @Column(length = 500)
    private String specialRequest;

    @Column(length = 500)
    private String travelerNeeds;

    private String pendingClarificationCode;

    @Column(length = 500)
    private String pendingClarificationValue;

    @Column(length = 1000)
    private String pendingClarificationNote;

    @Column(nullable = false)
    private String conversationState;

    @Lob
    @Column(nullable = false)
    private String tripSnapshotJson;

    @Column(nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public Integer getTravelers() { return travelers; }
    public void setTravelers(Integer travelers) { this.travelers = travelers; }
    public Integer getBudget() { return budget; }
    public void setBudget(Integer budget) { this.budget = budget; }
    public String getDepartureDate() { return departureDate; }
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }
    public String getReturnDate() { return returnDate; }
    public void setReturnDate(String returnDate) { this.returnDate = returnDate; }
    public String getTripType() { return tripType; }
    public void setTripType(String tripType) { this.tripType = tripType; }
    public String getBudgetCurrency() { return budgetCurrency; }
    public void setBudgetCurrency(String budgetCurrency) { this.budgetCurrency = budgetCurrency; }
    public String getBudgetScope() { return budgetScope; }
    public void setBudgetScope(String budgetScope) { this.budgetScope = budgetScope; }
    public String getVibe() { return vibe; }
    public void setVibe(String vibe) { this.vibe = vibe; }
    public String getSpecialRequest() { return specialRequest; }
    public void setSpecialRequest(String specialRequest) { this.specialRequest = specialRequest; }
    public String getTravelerNeeds() { return travelerNeeds; }
    public void setTravelerNeeds(String travelerNeeds) { this.travelerNeeds = travelerNeeds; }
    public String getPendingClarificationCode() { return pendingClarificationCode; }
    public void setPendingClarificationCode(String pendingClarificationCode) { this.pendingClarificationCode = pendingClarificationCode; }
    public String getPendingClarificationValue() { return pendingClarificationValue; }
    public void setPendingClarificationValue(String pendingClarificationValue) { this.pendingClarificationValue = pendingClarificationValue; }
    public String getPendingClarificationNote() { return pendingClarificationNote; }
    public void setPendingClarificationNote(String pendingClarificationNote) { this.pendingClarificationNote = pendingClarificationNote; }
    public String getConversationState() { return conversationState; }
    public void setConversationState(String conversationState) { this.conversationState = conversationState; }
    public String getTripSnapshotJson() { return tripSnapshotJson; }
    public void setTripSnapshotJson(String tripSnapshotJson) { this.tripSnapshotJson = tripSnapshotJson; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
