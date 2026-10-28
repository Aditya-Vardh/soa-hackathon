package edu.soa.s09.t09.feedbackackservice;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/feedback")
public class FeedbackController {
    private final FeedbackRepository feedbackRepository;
    private final SecurityHelper securityHelper;

    public FeedbackController(FeedbackRepository feedbackRepository, SecurityHelper securityHelper) {
        this.feedbackRepository = feedbackRepository;
        this.securityHelper = securityHelper;
    }

    @PostConstruct
    public void seedInitialFeedback() {
        if (feedbackRepository.count() == 0) {
            Feedback f1 = new Feedback("learner1", "1", "Check boundary conditions on node deletion. Ensure tree depth rebalancing is verified before completing.", "instructor1");
            feedbackRepository.save(f1);

            Feedback f2 = new Feedback("learner2", "2", "Deadlock caused by out-of-order lock acquisition across partitions. Implement two-phase locking sequence.", "instructor1");
            feedbackRepository.save(f2);
        }
    }

    @PostMapping
    public Feedback issue(@RequestParam(name = "learnerId", required = false) String paramLearnerId,
                          @RequestParam(name = "attemptId", required = false) String paramAttemptId,
                          @RequestParam(name = "text", required = false) String paramText,
                          @RequestBody(required = false) Map<String, String> body,
                          HttpServletRequest request) {
        String learnerId = (body != null && body.containsKey("learnerId")) ? body.get("learnerId") : paramLearnerId;
        String attemptId = (body != null && body.containsKey("attemptId")) ? body.get("attemptId") : paramAttemptId;
        String text = (body != null && body.containsKey("text")) ? body.get("text") : paramText;

        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        if (caller.isLearner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only instructors can issue lab feedback");
        }

        if (learnerId == null || learnerId.isBlank() || attemptId == null || attemptId.isBlank() || text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "learnerId, attemptId and text are required");
        }

        String instructorId = caller.userId != null ? caller.userId : "instructor1";
        Feedback feedback = new Feedback(learnerId.trim(), attemptId.trim(), text.trim(), instructorId);
        return feedbackRepository.save(feedback);
    }

    @GetMapping
    public List<Feedback> list(@RequestParam(name = "learnerId", required = false) String learnerId,
                              @RequestParam(name = "attemptId", required = false) String attemptId,
                              HttpServletRequest request) {
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);

        // Security: Learner can never see another learner's feedback
        if (caller.isLearner()) {
            learnerId = caller.userId;
        }

        List<Feedback> list;
        if (learnerId != null && !learnerId.isBlank()) {
            list = feedbackRepository.findByLearnerIdOrderByIdDesc(learnerId.trim());
        } else {
            list = feedbackRepository.findAllByOrderByIdDesc();
        }

        if (attemptId != null && !attemptId.isBlank()) {
            list = list.stream().filter(f -> f.getAttemptId().equalsIgnoreCase(attemptId.trim())).toList();
        }
        return list;
    }

    @GetMapping("/{id}")
    public Feedback get(@PathVariable("id") long id, HttpServletRequest request) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));

        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        securityHelper.verifyLearnerAccess(caller, feedback.getLearnerId());

        return feedback;
    }

    @GetMapping("/attempts/{attemptId}/status")
    public Map<String, Object> status(@PathVariable("attemptId") String attemptId) {
        return feedbackRepository.findTopByAttemptIdOrderByIdDesc(attemptId)
                .map(feedback -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("attemptId", attemptId);
                    result.put("feedbackId", feedback.getId());
                    result.put("acknowledged", feedback.isAcknowledged());
                    result.put("learnerId", feedback.getLearnerId());
                    result.put("text", feedback.getText());
                    result.put("found", true);
                    return result;
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found for attempt " + attemptId));
    }

    @PostMapping("/{id}/acknowledge")
    public Feedback acknowledge(@PathVariable("id") long id,
                                @RequestParam(name = "learnerId", required = false) String paramLearnerId,
                                HttpServletRequest request) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));

        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        String learnerId = paramLearnerId;
        if (caller.userId != null) {
            learnerId = caller.userId;
        }

        if (learnerId != null && !feedback.getLearnerId().equals(learnerId.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Feedback belongs to another learner");
        }

        feedback.setAcknowledged(true);
        feedback.setAcknowledgedAt(Instant.now());
        return feedbackRepository.save(feedback);
    }
}
