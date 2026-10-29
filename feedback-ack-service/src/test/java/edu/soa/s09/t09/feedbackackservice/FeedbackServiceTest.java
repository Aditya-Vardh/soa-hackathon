package edu.soa.s09.t09.feedbackackservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackServiceTest {
    private FeedbackRepository feedbackRepository;
    private SecurityHelper securityHelper;
    private FeedbackController feedbackController;
    private Map<Long, Feedback> feedbackMap;

    @BeforeEach
    void setUp() {
        feedbackMap = new HashMap<>();
        feedbackRepository = (FeedbackRepository) Proxy.newProxyInstance(
                FeedbackRepository.class.getClassLoader(),
                new Class<?>[]{FeedbackRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName()) && args != null && args.length > 0) {
                        return Optional.ofNullable(feedbackMap.get(args[0]));
                    }
                    if ("save".equals(method.getName()) && args != null && args.length > 0) {
                        Feedback f = (Feedback) args[0];
                        if (f.getId() == null) f.setId((long) (feedbackMap.size() + 1));
                        feedbackMap.put(f.getId(), f);
                        return f;
                    }
                    if ("count".equals(method.getName())) {
                        return (long) feedbackMap.size();
                    }
                    return null;
                }
        );
        securityHelper = new SecurityHelper();
        feedbackController = new FeedbackController(feedbackRepository, securityHelper);
    }

    @Test
    void learnerCannotAcknowledgeAnotherLearnersFeedback() {
        Feedback f = new Feedback("learner2", "attempt-1", "Review sorting invariant", "instructor1");
        f.setId(10L);
        feedbackMap.put(10L, f);

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
        feedbackMap.put(10L, f);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "learner1");
        request.addHeader("X-User-Role", "ROLE_LEARNER");

        Feedback result = feedbackController.acknowledge(10L, "learner1", request);
        assertThat(result.isAcknowledged()).isTrue();
        assertThat(result.getAcknowledgedAt()).isNotNull();
    }
}
