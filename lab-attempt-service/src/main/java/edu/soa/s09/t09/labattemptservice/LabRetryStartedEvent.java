package edu.soa.s09.t09.labattemptservice;

public class LabRetryStartedEvent {
    private String eventId;
    private String eventType = "LabRetryStarted";
    private String attemptId;
    private String learnerId;
    private Long timestamp;

    public LabRetryStartedEvent() {}

    public LabRetryStartedEvent(String eventId, String attemptId, String learnerId, Long timestamp) {
        this.eventId = eventId;
        this.eventType = "LabRetryStarted";
        this.attemptId = attemptId;
        this.learnerId = learnerId;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}

