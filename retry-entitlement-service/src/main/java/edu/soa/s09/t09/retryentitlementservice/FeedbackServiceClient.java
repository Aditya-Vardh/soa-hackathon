package edu.soa.s09.t09.retryentitlementservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@Component
public class FeedbackServiceClient {
    private static final Logger log = LoggerFactory.getLogger(FeedbackServiceClient.class);
    private final RestTemplate restTemplate;

    @Value("${feedback.service.url:http://localhost:8083}")
    private String feedbackServiceUrl;

    public FeedbackServiceClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(2000))
                .setReadTimeout(Duration.ofMillis(2000))
                .build();
    }

    public boolean isFeedbackAcknowledged(String attemptId) {
        if (attemptId == null || attemptId.isBlank()) {
            return false;
        }
        // Extract numeric ID if formatted as attempt-1
        String cleanId = attemptId.replaceAll("[^0-9]", "");
        String targetId = cleanId.isEmpty() ? attemptId : cleanId;

        String url = feedbackServiceUrl + "/feedback/attempts/" + targetId + "/status";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object ack = response.getBody().get("acknowledged");
                return Boolean.TRUE.equals(ack);
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Feedback not found for attempt {}", attemptId);
            return false;
        } catch (Exception ex) {
            log.warn("Could not query feedback service at {}: {}", url, ex.getMessage());
            // If service is unreachable, we treat as not acknowledged to enforce policy
            return false;
        }
        return false;
    }
}

