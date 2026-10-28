package edu.soa.s09.t09.labattemptservice;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attempts")
public class AttemptController {
    private final AttemptRepository attemptRepository;
    private final SecurityHelper securityHelper;

    public AttemptController(AttemptRepository attemptRepository, SecurityHelper securityHelper) {
        this.attemptRepository = attemptRepository;
        this.securityHelper = securityHelper;
    }

    @PostConstruct
    public void seedInitialAttempts() {
        if (attemptRepository.count() == 0) {
            Attempt a1 = new Attempt("learner1", "lab-09");
            a1.setStatus("FAILED");
            a1.setScore(45);
            a1.setNotes("NullPointerException in binary search tree traversal at node depth 4.");
            attemptRepository.save(a1);

            Attempt a2 = new Attempt("learner2", "lab-04");
            a2.setStatus("FAILED");
            a2.setScore(52);
            a2.setNotes("Deadlock detected during distributed transaction rollback.");
            attemptRepository.save(a2);
        }
    }

    @PostMapping
    public Attempt create(@RequestParam(name = "learnerId", required = false) String paramLearnerId,
                          @RequestParam(name = "labId", required = false) String paramLabId,
                          @RequestParam(name = "notes", required = false) String notes,
                          @RequestBody(required = false) Map<String, String> body,
                          HttpServletRequest request) {
        String learnerId = (body != null && body.containsKey("learnerId")) ? body.get("learnerId") : paramLearnerId;
        String labId = (body != null && body.containsKey("labId")) ? body.get("labId") : paramLabId;
        String noteText = (body != null && body.containsKey("notes")) ? body.get("notes") : notes;

        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        if (caller.isLearner()) {
            // Learner can only create attempts for themselves
            learnerId = caller.userId;
        }

        if (learnerId == null || learnerId.isBlank() || labId == null || labId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "learnerId and labId are required");
        }

        Attempt attempt = new Attempt(learnerId.trim(), labId.trim());
        if (noteText != null && !noteText.isBlank()) {
            attempt.setNotes(noteText.trim());
        }
        return attemptRepository.save(attempt);
    }

    @GetMapping
    public List<Attempt> list(@RequestParam(name = "learnerId", required = false) String learnerId,
                             @RequestParam(name = "labId", required = false) String labId,
                             @RequestParam(name = "status", required = false) String status,
                             HttpServletRequest request) {
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);

        // Record-level Authorization: Learner can NEVER view another learner's attempts
        if (caller.isLearner()) {
            learnerId = caller.userId;
        }

        List<Attempt> list;
        if (learnerId != null && !learnerId.isBlank()) {
            list = attemptRepository.findByLearnerIdOrderByIdDesc(learnerId.trim());
        } else {
            list = attemptRepository.findAllByOrderByIdDesc();
        }

        if (labId != null && !labId.isBlank()) {
            list = list.stream().filter(a -> a.getLabId().equalsIgnoreCase(labId.trim())).toList();
        }
        if (status != null && !status.isBlank()) {
            list = list.stream().filter(a -> a.getStatus().equalsIgnoreCase(status.trim())).toList();
        }
        return list;
    }

    @GetMapping("/{id}")
    public Attempt get(@PathVariable("id") long id, HttpServletRequest request) {
        Attempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        securityHelper.verifyLearnerAccess(caller, attempt.getLearnerId());

        return attempt;
    }

    @PostMapping("/{id}/status")
    public Attempt updateStatus(@PathVariable("id") long id,
                                @RequestParam("status") String status,
                                HttpServletRequest request) {
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        Attempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (caller.isLearner()) {
            securityHelper.verifyLearnerAccess(caller, attempt.getLearnerId());
        }
        attempt.setStatus(status);
        return attemptRepository.save(attempt);
    }
}
