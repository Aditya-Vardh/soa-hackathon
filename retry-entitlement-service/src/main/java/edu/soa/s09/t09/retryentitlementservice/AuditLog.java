package edu.soa.s09.t09.retryentitlementservice;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String action; // SEED, RESERVE, CANCEL, START, REQUEST_EXTRA, APPROVE_EXTRA, REJECT_EXTRA

    @Column(nullable = false)
    private String learnerId;

    private String attemptId;

    private String performedBy;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public AuditLog() {}

    public AuditLog(String action, String learnerId, String attemptId, String performedBy, String details) {
        this.action = action;
        this.learnerId = learnerId;
        this.attemptId = attemptId;
        this.performedBy = performedBy;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getLearnerId() { return learnerId; }
    public String getAttemptId() { return attemptId; }
    public String getPerformedBy() { return performedBy; }
    public String getDetails() { return details; }
    public Instant getTimestamp() { return timestamp; }
}

