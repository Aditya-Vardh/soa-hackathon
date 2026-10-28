package edu.soa.s09.t09.retryentitlementservice;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "entitlements")
public class Entitlement {
    @Id
    private String learnerId;

    @Column(nullable = false)
    private int tokens = 2;

    private String reservedAttemptId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "entitlement_reservations", joinColumns = @JoinColumn(name = "learner_id"))
    @Column(name = "attempt_id")
    private Set<String> reservations = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "entitlement_started_attempts", joinColumns = @JoinColumn(name = "learner_id"))
    @Column(name = "attempt_id")
    private Set<String> startedAttempts = new HashSet<>();

    private Instant updatedAt = Instant.now();

    public Entitlement() {}

    public Entitlement(String learnerId, int tokens) {
        this.learnerId = learnerId;
        this.tokens = tokens;
        this.updatedAt = Instant.now();
    }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public int getTokens() { return tokens; }
    public void setTokens(int tokens) { this.tokens = tokens; this.updatedAt = Instant.now(); }

    public String getReservedAttemptId() {
        if (reservations != null && !reservations.isEmpty()) {
            return reservations.iterator().next();
        }
        return reservedAttemptId;
    }

    public void setReservedAttemptId(String reservedAttemptId) {
        this.reservedAttemptId = reservedAttemptId;
    }

    public Set<String> getReservations() { return reservations; }
    public void setReservations(Set<String> reservations) { this.reservations = reservations; }

    public Set<String> getStartedAttempts() { return startedAttempts; }
    public void setStartedAttempts(Set<String> startedAttempts) { this.startedAttempts = startedAttempts; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

