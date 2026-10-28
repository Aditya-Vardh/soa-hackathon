package edu.soa.s09.t09.retryentitlementservice;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "retry_requests")
public class RetryRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String learnerId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    private String decisionReason;

    private String reviewedBy;

    private int grantedTokens = 1;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    private Instant reviewedAt;

    public RetryRequest() {}

    public RetryRequest(String learnerId, String reason) {
        this.learnerId = learnerId;
        this.reason = reason;
        this.status = "PENDING";
        this.requestedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public int getGrantedTokens() { return grantedTokens; }
    public void setGrantedTokens(int grantedTokens) { this.grantedTokens = grantedTokens; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}

