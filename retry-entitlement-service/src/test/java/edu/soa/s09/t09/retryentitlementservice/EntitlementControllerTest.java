package edu.soa.s09.t09.retryentitlementservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntitlementControllerTest {
    private EntitlementRepository entitlementRepository;
    private AuditLogRepository auditLogRepository;
    private RetryRequestRepository retryRequestRepository;
    private SecurityHelper securityHelper;
    private FeedbackServiceClient feedbackServiceClient;
    private RetryEventProducer retryEventProducer;
    private EntitlementController controller;

    private Map<String, Entitlement> entitlementMap;
    private List<AuditLog> auditLogs;
    private Map<Long, RetryRequest> retryRequests;
    private boolean feedbackAckResult;
    private List<String> publishedEvents;

    @BeforeEach
    void setUp() {
        entitlementMap = new HashMap<>();
        auditLogs = new ArrayList<>();
        retryRequests = new HashMap<>();
        feedbackAckResult = true;
        publishedEvents = new ArrayList<>();

        entitlementRepository = (EntitlementRepository) Proxy.newProxyInstance(
                EntitlementRepository.class.getClassLoader(),
                new Class<?>[]{EntitlementRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName()) && args != null && args.length > 0) {
                        return Optional.ofNullable(entitlementMap.get(args[0]));
                    }
                    if ("save".equals(method.getName()) && args != null && args.length > 0) {
                        Entitlement e = (Entitlement) args[0];
                        entitlementMap.put(e.getLearnerId(), e);
                        return e;
                    }
                    if ("existsById".equals(method.getName()) && args != null && args.length > 0) {
                        return entitlementMap.containsKey(args[0]);
                    }
                    return null;
                }
        );

        auditLogRepository = (AuditLogRepository) Proxy.newProxyInstance(
                AuditLogRepository.class.getClassLoader(),
                new Class<?>[]{AuditLogRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName()) && args != null && args.length > 0) {
                        AuditLog log = (AuditLog) args[0];
                        auditLogs.add(log);
                        return log;
                    }
                    if ("findAllByOrderByIdDesc".equals(method.getName())) {
                        return auditLogs;
                    }
                    return null;
                }
        );

        retryRequestRepository = (RetryRequestRepository) Proxy.newProxyInstance(
                RetryRequestRepository.class.getClassLoader(),
                new Class<?>[]{RetryRequestRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName()) && args != null && args.length > 0) {
                        return Optional.ofNullable(retryRequests.get(args[0]));
                    }
                    if ("save".equals(method.getName()) && args != null && args.length > 0) {
                        RetryRequest r = (RetryRequest) args[0];
                        if (r.getId() == null) r.setId((long) (retryRequests.size() + 1));
                        retryRequests.put(r.getId(), r);
                        return r;
                    }
                    return null;
                }
        );

        securityHelper = new SecurityHelper();

        feedbackServiceClient = new FeedbackServiceClient(new org.springframework.boot.web.client.RestTemplateBuilder()) {
            @Override
            public boolean isFeedbackAcknowledged(String attemptId) {
                return feedbackAckResult;
            }
        };

        retryEventProducer = new RetryEventProducer(null, new com.fasterxml.jackson.databind.ObjectMapper()) {
            @Override
            public void publishRetryStarted(String attemptId, String learnerId) {
                publishedEvents.add(attemptId + ":" + learnerId);
            }
        };

        controller = new EntitlementController(
                entitlementRepository,
                auditLogRepository,
                retryRequestRepository,
                securityHelper,
                feedbackServiceClient,
                retryEventProducer
        );
    }

    @Test
    void cancellationPreservesTokenAndStartConsumesIt() {
        Entitlement entitlement = new Entitlement("learner1", 2);
        entitlementMap.put("learner1", entitlement);
        feedbackAckResult = true;

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "learner1");
        request.addHeader("X-User-Role", "ROLE_LEARNER");

        // 1. Reserve attempt-1
        controller.reserve("learner1", "attempt-1", request);
        assertThat(entitlement.getReservations()).contains("attempt-1");
        assertThat(entitlement.getTokens()).isEqualTo(2); // Reserving does NOT consume token

        // 2. Cancel attempt-1
        controller.cancel("learner1", "attempt-1", request);
        assertThat(entitlement.getReservations()).doesNotContain("attempt-1");
        assertThat(entitlement.getTokens()).isEqualTo(2); // Cancelling preserves token count

        // 3. Reserve again and start
        controller.reserve("learner1", "attempt-1", request);
        controller.start("learner1", "attempt-1", true, request);
        assertThat(entitlement.getTokens()).isEqualTo(1); // Starting consumes exactly 1 token
        assertThat(entitlement.getReservations()).doesNotContain("attempt-1");
        assertThat(entitlement.getStartedAttempts()).contains("attempt-1");

        // Verify Kafka event was emitted
        assertThat(publishedEvents).contains("attempt-1:learner1");
    }

    @Test
    void startFailsWhenFeedbackIsNotAcknowledged() {
        Entitlement entitlement = new Entitlement("learner1", 2);
        entitlement.getReservations().add("attempt-1");
        entitlementMap.put("learner1", entitlement);
        feedbackAckResult = false; // NOT acknowledged

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "learner1");
        request.addHeader("X-User-Role", "ROLE_LEARNER");

        assertThatThrownBy(() -> controller.start("learner1", "attempt-1", true, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Feedback acknowledgement is required");
    }

    @Test
    void reservationIsRejectedWhenTokensExhausted() {
        Entitlement entitlement = new Entitlement("learner1", 1);
        entitlement.getReservations().add("attempt-1");
        entitlementMap.put("learner1", entitlement);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "learner1");

        assertThatThrownBy(() -> controller.reserve("learner1", "attempt-2", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No token available");
    }

    @Test
    void learnerCannotAccessAnotherLearnersEntitlement() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "learner1");
        request.addHeader("X-User-Role", "ROLE_LEARNER");

        assertThatThrownBy(() -> controller.get("learner2", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void instructorApprovalGrantsTokenWithoutAutoStartingRetry() {
        RetryRequest req = new RetryRequest("learner1", "Need to retry lab after server crash");
        req.setId(101L);
        retryRequests.put(101L, req);

        Entitlement entitlement = new Entitlement("learner1", 0);
        entitlementMap.put("learner1", entitlement);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "instructor1");
        request.addHeader("X-User-Role", "ROLE_INSTRUCTOR");

        RetryRequest reviewed = controller.reviewRequest(101L, true, "Approved for 1 retry", 1, request);

        assertThat(reviewed.getStatus()).isEqualTo("APPROVED");
        assertThat(entitlement.getTokens()).isEqualTo(1); // 1 token added to balance
        assertThat(entitlement.getReservations()).isEmpty(); // NOT auto-reserved
        assertThat(entitlement.getStartedAttempts()).isEmpty(); // NOT auto-started
    }
}
