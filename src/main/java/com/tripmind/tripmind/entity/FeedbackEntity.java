package com.tripmind.tripmind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "feedback")
public class FeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String requestId;

    private Boolean useful;
    private Boolean budgetRealistic;
    private Boolean wouldBook;

    @Column(length = 1000)
    private String comments;

    @Column(nullable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Boolean getUseful() { return useful; }
    public void setUseful(Boolean useful) { this.useful = useful; }
    public Boolean getBudgetRealistic() { return budgetRealistic; }
    public void setBudgetRealistic(Boolean budgetRealistic) { this.budgetRealistic = budgetRealistic; }
    public Boolean getWouldBook() { return wouldBook; }
    public void setWouldBook(Boolean wouldBook) { this.wouldBook = wouldBook; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
