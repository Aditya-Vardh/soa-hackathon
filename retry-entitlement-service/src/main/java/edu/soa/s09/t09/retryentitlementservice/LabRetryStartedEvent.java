package edu.soa.s09.t09.retryentitlementservice;

import java.time.Instant;

public class LabRetryStartedEvent {
    private String eventId;
    private String aggregateId;
    private String schemaVersion = "1.0.0";
    private String occurredAt = Instant.now().toString();
    private String correlationId;
    private String eventType = "LabRetryStarted";
    private String attemptId;
    private String learnerId;
    private Long timestamp;

    public LabRetryStartedEvent() {}

    public LabRetryStartedEvent(String eventId, String attemptId, String learnerId, Long timestamp) {
        this.eventId = eventId;
        this.aggregateId = attemptId;
        this.schemaVersion = "1.0.0";
        this.occurredAt = Instant.now().toString();
        this.correlationId = "corr-" + eventId;
        this.eventType = "LabRetryStarted";
        this.attemptId = attemptId;
        this.learnerId = learnerId;
        this.timestamp = timestamp != null ? timestamp : System.currentTimeMillis();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
