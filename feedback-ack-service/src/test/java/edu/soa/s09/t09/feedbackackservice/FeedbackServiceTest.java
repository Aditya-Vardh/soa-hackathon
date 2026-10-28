package edu.soa.s09.t09.feedbackackservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FeedbackServiceTest {
    private FeedbackRepository feedbackRepository;
    private SecurityHelper securityHelper;
    private FeedbackController feedbackController;

    @BeforeEach
    void setUp() {
        feedbackRepository = mock(FeedbackRepository.class);
        securityHelper = new SecurityHelper();
        feedbackController = new FeedbackController(feedbackRepository, securityHelper);
    }

    @Test
    void learnerCannotAcknowledgeAnotherLearnersFeedback() {
        Feedback f = new Feedback("learner2", "attempt-1", "Review sorting invariant", "instructor1");
        f.setId(10L);
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(f));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "learner1");
        request.addHeader("X-User-Role", "ROLE_LEARNER");

        assertThatThrownBy(() -> feedbackController.acknowledge(10L, "learner1", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Feedback belongs to another learner");
    }

    @Test
    void learnerCanAcknowledgeOwnFeedback() {
        Feedback f = new Feedback("learner1", "attempt-1", "Review sorting invariant", "instructor1");
        f.setId(10L);
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(f));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(i -> i.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "learner1");
        request.addHeader("X-User-Role", "ROLE_LEARNER");

        Feedback result = feedbackController.acknowledge(10L, "learner1", request);
        assertThat(result.isAcknowledged()).isTrue();
        assertThat(result.getAcknowledgedAt()).isNotNull();
    }
}

