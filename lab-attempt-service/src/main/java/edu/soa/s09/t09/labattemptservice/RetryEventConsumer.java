package edu.soa.s09.t09.labattemptservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RetryEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(RetryEventConsumer.class);
    private static final String DLQ_TOPIC = "soa.s09.t09.events.DLQ";

    private final AttemptRepository attemptRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public RetryEventConsumer(AttemptRepository attemptRepository,
                              ProcessedEventRepository processedEventRepository,
                              ObjectMapper objectMapper,
                              KafkaTemplate<String, Object> kafkaTemplate) {
        this.attemptRepository = attemptRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "soa.s09.t09.events", groupId = "lab-attempt-service-group", autoStartup = "${spring.kafka.consumer.auto-startup:true}")
    @Transactional
    public void onMessage(String payload) {
        log.info("Received event payload on soa.s09.t09.events: {}", payload);
        LabRetryStartedEvent event;
        try {
            event = objectMapper.readValue(payload, LabRetryStartedEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse event JSON, forwarding to DLQ: {}", e.getMessage());
            sendToDlq(payload, "JSON_PARSE_ERROR: " + e.getMessage());
            return;
        }

        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            log.warn("Invalid event format received, missing eventId. Forwarding to DLQ.");
            sendToDlq(payload, "MISSING_EVENT_ID");
            return;
        }

        // Idempotent Deduplication Check (CO1)
        if (processedEventRepository.existsById(event.getEventId())) {
            log.info("Duplicate event detected with id {}. Skipping processing (idempotent).", event.getEventId());
            return;
        }

        // Record processed event
        processedEventRepository.save(new ProcessedEvent(event.getEventId(), event.getEventType()));

        // Process business effect: Transition attempt status to RETRY_STARTED
        if (event.getAttemptId() != null) {
            try {
                long attemptId = Long.parseLong(event.getAttemptId().replaceAll("[^0-9]", ""));
                attemptRepository.findById(attemptId).ifPresent(attempt -> {
                    attempt.setStatus("RETRY_STARTED");
                    attemptRepository.save(attempt);
                    log.info("Successfully updated attempt #{} to RETRY_STARTED via event id {}", attemptId, event.getEventId());
                });
            } catch (Exception e) {
                log.warn("Could not parse numeric attemptId from {}: {}", event.getAttemptId(), e.getMessage());
            }
        }
    }

    private void sendToDlq(String payload, String errorReason) {
        try {
            kafkaTemplate.send(DLQ_TOPIC, errorReason, payload);
            log.info("Successfully published message to DLQ {}", DLQ_TOPIC);
        } catch (Exception ex) {
            log.warn("Could not publish to DLQ topic {}: {}", DLQ_TOPIC, ex.getMessage());
        }
    }
}

