package edu.soa.s09.t09.labattemptservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptServiceTest {
    private AttemptRepository attemptRepository;
    private ProcessedEventRepository processedEventRepository;
    private SecurityHelper securityHelper;
    private AttemptController attemptController;
    private RetryEventConsumer retryEventConsumer;
    private Map<Long, Attempt> attemptMap;
    private Set<String> processedEvents;
    private int saveCallCount;

    @BeforeEach
    void setUp() {
        attemptMap = new HashMap<>();
        processedEvents = new HashSet<>();
        saveCallCount = 0;

        attemptRepository = (AttemptRepository) Proxy.newProxyInstance(
                AttemptRepository.class.getClassLoader(),
                new Class<?>[]{AttemptRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName()) && args != null && args.length > 0) {
                        return Optional.ofNullable(attemptMap.get(args[0]));
                    }
                    if ("save".equals(method.getName()) && args != null && args.length > 0) {
                        saveCallCount++;
                        Attempt a = (Attempt) args[0];
                        if (a.getId() == null) a.setId((long) (attemptMap.size() + 1));
                        attemptMap.put(a.getId(), a);
                        return a;
                    }
                    if ("count".equals(method.getName())) {
                        return (long) attemptMap.size();
                    }
                    return null;
                }
        );

        processedEventRepository = (ProcessedEventRepository) Proxy.newProxyInstance(
                ProcessedEventRepository.class.getClassLoader(),
                new Class<?>[]{ProcessedEventRepository.class},
                (proxy, method, args) -> {
                    if ("existsById".equals(method.getName()) && args != null && args.length > 0) {
                        return processedEvents.contains(args[0]);
                    }
                    if ("save".equals(method.getName()) && args != null && args.length > 0) {
                        ProcessedEvent pe = (ProcessedEvent) args[0];
                        processedEvents.add(pe.getEventId());
                        return pe;
                    }
                    return null;
                }
        );

        securityHelper = new SecurityHelper();
        attemptController = new AttemptController(attemptRepository, securityHelper);
        retryEventConsumer = new RetryEventConsumer(attemptRepository, processedEventRepository, new ObjectMapper(), null);
    }

    @Test
    void learnerCannotAccessAnotherLearnersAttempt() {
        Attempt otherAttempt = new Attempt(10L, "learner2", "lab-09", "FAILED");
        attemptMap.put(10L, otherAttempt);

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
        attemptMap.put(10L, otherAttempt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "instructor1");
        request.addHeader("X-User-Role", "ROLE_INSTRUCTOR");

        Attempt result = attemptController.get(10L, request);
        assertThat(result.getLearnerId()).isEqualTo("learner2");
    }

    @Test
    void duplicateKafkaEventsAreDeduplicatedIdempotently() {
        String eventId = "evt-12345";
        processedEvents.add(eventId); // Already processed

        retryEventConsumer.onMessage("{\"eventId\":\"" + eventId + "\",\"eventType\":\"LabRetryStarted\",\"aggregateId\":\"attempt-10\",\"schemaVersion\":\"1.0.0\",\"occurredAt\":\"2026-01-01T00:00:00Z\",\"correlationId\":\"corr-" + eventId + "\",\"attemptId\":\"10\",\"learnerId\":\"learner1\"}");

        // Verify attempt repository was never updated
        assertThat(saveCallCount).isEqualTo(0);
    }
}
