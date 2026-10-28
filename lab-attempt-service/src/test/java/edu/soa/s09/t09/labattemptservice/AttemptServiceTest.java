package edu.soa.s09.t09.labattemptservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AttemptServiceTest {
    private AttemptRepository attemptRepository;
    private ProcessedEventRepository processedEventRepository;
    private SecurityHelper securityHelper;
    private AttemptController attemptController;
    private RetryEventConsumer retryEventConsumer;
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        attemptRepository = mock(AttemptRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        securityHelper = new SecurityHelper();
        attemptController = new AttemptController(attemptRepository, securityHelper);
        kafkaTemplate = mock(KafkaTemplate.class);
        retryEventConsumer = new RetryEventConsumer(attemptRepository, processedEventRepository, new ObjectMapper(), kafkaTemplate);
    }

    @Test
    void learnerCannotAccessAnotherLearnersAttempt() {
        Attempt otherAttempt = new Attempt(10L, "learner2", "lab-09", "FAILED");
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(otherAttempt));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "learner1");
        request.addHeader("X-User-Role", "ROLE_LEARNER");

        assertThatThrownBy(() -> attemptController.get(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void instructorCanAccessAnyLearnerAttempt() {
        Attempt otherAttempt = new Attempt(10L, "learner2", "lab-09", "FAILED");
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(otherAttempt));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "instructor1");
        request.addHeader("X-User-Role", "ROLE_INSTRUCTOR");

        Attempt result = attemptController.get(10L, request);
        assertThat(result.getLearnerId()).isEqualTo("learner2");
    }

    @Test
    void duplicateKafkaEventsAreDeduplicatedIdempotently() {
        String eventId = "evt-12345";
        LabRetryStartedEvent event = new LabRetryStartedEvent(eventId, "attempt-10", "learner1", System.currentTimeMillis());

        // Event already processed
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        retryEventConsumer.onMessage("{\"eventId\":\"" + eventId + "\",\"eventType\":\"LabRetryStarted\",\"attemptId\":\"10\",\"learnerId\":\"learner1\"}");

        // Verify attempt repository was never updated
        verify(attemptRepository, never()).save(any());
    }
}

