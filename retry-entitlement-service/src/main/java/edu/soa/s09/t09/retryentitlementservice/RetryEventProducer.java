package edu.soa.s09.t09.retryentitlementservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RetryEventProducer {
    private static final Logger log = LoggerFactory.getLogger(RetryEventProducer.class);
    public static final String TOPIC = "soa.s09.t09.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RetryEventProducer(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishRetryStarted(String attemptId, String learnerId) {
        String eventId = "evt-" + UUID.randomUUID();
        LabRetryStartedEvent event = new LabRetryStartedEvent(eventId, attemptId, learnerId, System.currentTimeMillis());

        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, learnerId, json);
            log.info("Published LabRetryStarted event {} to topic {}", eventId, TOPIC);
        } catch (Exception e) {
            log.warn("Failed to publish LabRetryStarted event to Kafka: {}", e.getMessage());
        }
    }
}

