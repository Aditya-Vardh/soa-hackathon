package edu.soa.s09.t09.retryentitlementservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EntitlementControllerTest {
    private EntitlementRepository entitlementRepository;
    private AuditLogRepository auditLogRepository;
    private RetryRequestRepository retryRequestRepository;
    private SecurityHelper securityHelper;
    private FeedbackServiceClient feedbackServiceClient;
    private RetryEventProducer retryEventProducer;
    private EntitlementController controller;

    @BeforeEach
    void setUp() {
        entitlementRepository = mock(EntitlementRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        retryRequestRepository = mock(RetryRequestRepository.class);
        securityHelper = new SecurityHelper();
        feedbackServiceClient = mock(FeedbackServiceClient.class);
        retryEventProducer = mock(RetryEventProducer.class);

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
        when(entitlementRepository.findById("learner1")).thenReturn(Optional.of(entitlement));
        when(entitlementRepository.save(any(Entitlement.class))).thenAnswer(i -> i.getArgument(0));
        when(feedbackServiceClient.isFeedbackAcknowledged("attempt-1")).thenReturn(true);

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
        verify(retryEventProducer).publishRetryStarted("attempt-1", "learner1");
    }

    @Test
    void startFailsWhenFeedbackIsNotAcknowledged() {
        Entitlement entitlement = new Entitlement("learner1", 2);
        entitlement.getReservations().add("attempt-1");
        when(entitlementRepository.findById("learner1")).thenReturn(Optional.of(entitlement));
        when(feedbackServiceClient.isFeedbackAcknowledged("attempt-1")).thenReturn(false);

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
        when(entitlementRepository.findById("learner1")).thenReturn(Optional.of(entitlement));

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
        when(retryRequestRepository.findById(101L)).thenReturn(Optional.of(req));
        when(retryRequestRepository.save(any(RetryRequest.class))).thenAnswer(i -> i.getArgument(0));

        Entitlement entitlement = new Entitlement("learner1", 0);
        when(entitlementRepository.findById("learner1")).thenReturn(Optional.of(entitlement));
        when(entitlementRepository.save(any(Entitlement.class))).thenAnswer(i -> i.getArgument(0));

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
