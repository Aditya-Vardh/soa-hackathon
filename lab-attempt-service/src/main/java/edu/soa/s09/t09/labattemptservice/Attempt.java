package edu.soa.s09.t09.labattemptservice;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "attempts")
public class Attempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String learnerId;

    @Column(nullable = false)
    private String labId;

    @Column(nullable = false)
    private String status = "FAILED"; // FAILED, RETRY_RESERVED, RETRY_STARTED, PASSED

    private Integer score = 42; // Seeded score for failed lab

    private String notes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public Attempt() {}

    public Attempt(String learnerId, String labId) {
        this.learnerId = learnerId;
        this.labId = labId;
        this.status = "FAILED";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Attempt(Long id, String learnerId, String labId, String status) {
        this.id = id;
        this.learnerId = learnerId;
        this.labId = labId;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public String getLabId() { return labId; }
    public void setLabId(String labId) { this.labId = labId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

