package edu.soa.s09.t09.feedbackackservice;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "feedback")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String learnerId;

    @Column(nullable = false)
    private String attemptId;

    private String instructorId = "instructor1";

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(nullable = false)
    private boolean acknowledged = false;

    private Instant acknowledgedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Feedback() {}

    public Feedback(String learnerId, String attemptId, String text, String instructorId) {
        this.learnerId = learnerId;
        this.attemptId = attemptId;
        this.text = text;
        this.instructorId = instructorId != null ? instructorId : "instructor1";
        this.acknowledged = false;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }

    public String getInstructorId() { return instructorId; }
    public void setInstructorId(String instructorId) { this.instructorId = instructorId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }

    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

