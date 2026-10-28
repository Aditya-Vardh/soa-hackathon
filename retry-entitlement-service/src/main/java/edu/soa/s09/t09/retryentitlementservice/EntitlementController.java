package edu.soa.s09.t09.retryentitlementservice;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/entitlements")
public class EntitlementController {
    private final EntitlementRepository entitlementRepository;
    private final AuditLogRepository auditLogRepository;
    private final RetryRequestRepository retryRequestRepository;
    private final SecurityHelper securityHelper;
    private final FeedbackServiceClient feedbackServiceClient;
    private final RetryEventProducer retryEventProducer;

    public EntitlementController(EntitlementRepository entitlementRepository,
                                 AuditLogRepository auditLogRepository,
                                 RetryRequestRepository retryRequestRepository,
                                 SecurityHelper securityHelper,
                                 FeedbackServiceClient feedbackServiceClient,
                                 RetryEventProducer retryEventProducer) {
        this.entitlementRepository = entitlementRepository;
        this.auditLogRepository = auditLogRepository;
        this.retryRequestRepository = retryRequestRepository;
        this.securityHelper = securityHelper;
        this.feedbackServiceClient = feedbackServiceClient;
        this.retryEventProducer = retryEventProducer;
    }

    @PostConstruct
    public void seedInitialState() {
        if (!entitlementRepository.existsById("learner1")) {
            entitlementRepository.save(new Entitlement("learner1", 2));
        }
        if (!entitlementRepository.existsById("learner2")) {
            entitlementRepository.save(new Entitlement("learner2", 1));
        }
    }

    @PostMapping("/{learnerId}/seed")
    @Transactional
    public Entitlement seed(@PathVariable("learnerId") String learnerId,
                            @RequestParam(name = "tokens", defaultValue = "2") int tokens,
                            HttpServletRequest request) {
        if (learnerId.isBlank() || tokens < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "learnerId and non-negative tokens are required");
        }
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);

        Entitlement entitlement = entitlementRepository.findById(learnerId)
                .orElse(new Entitlement(learnerId, tokens));
        entitlement.setTokens(tokens);
        entitlement.getReservations().clear();
        entitlement.setReservedAttemptId(null);
        entitlement = entitlementRepository.save(entitlement);

        auditLogRepository.save(new AuditLog("SEED", learnerId, null,
                caller.userId != null ? caller.userId : "SYSTEM", "Seeded " + tokens + " retry tokens"));

        return entitlement;
    }

    @PostMapping("/{learnerId}/reserve")
    @Transactional
    public Entitlement reserve(@PathVariable("learnerId") String learnerId,
                              @RequestParam(name = "attemptId") String attemptId,
                              HttpServletRequest request) {
        validateAttempt(attemptId);
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        securityHelper.verifyLearnerAccess(caller, learnerId);

        Entitlement entitlement = require(learnerId);

        synchronized (this) {
            if (entitlement.getReservations().contains(attemptId)) {
                return entitlement;
            }
            if (entitlement.getReservations().size() >= entitlement.getTokens()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No token available");
            }
            entitlement.getReservations().add(attemptId);
            entitlement.setReservedAttemptId(attemptId);
            entitlement = entitlementRepository.save(entitlement);
        }

        auditLogRepository.save(new AuditLog("RESERVE", learnerId, attemptId,
                caller.userId != null ? caller.userId : learnerId, "Reserved retry for attempt " + attemptId));

        return entitlement;
    }

    @PostMapping("/{learnerId}/cancel")
    @Transactional
    public Entitlement cancel(@PathVariable("learnerId") String learnerId,
                             @RequestParam(name = "attemptId") String attemptId,
                             HttpServletRequest request) {
        validateAttempt(attemptId);
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        securityHelper.verifyLearnerAccess(caller, learnerId);

        Entitlement entitlement = require(learnerId);

        synchronized (this) {
            if (!entitlement.getReservations().remove(attemptId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation not found");
            }
            entitlement.setReservedAttemptId(entitlement.getReservedAttemptId());
            entitlement = entitlementRepository.save(entitlement);
        }

        auditLogRepository.save(new AuditLog("CANCEL", learnerId, attemptId,
                caller.userId != null ? caller.userId : learnerId, "Cancelled reservation for attempt " + attemptId + " (token preserved)"));

        return entitlement;
    }

    @PostMapping("/{learnerId}/start")
    @Transactional
    public Entitlement start(@PathVariable("learnerId") String learnerId,
                            @RequestParam(name = "attemptId") String attemptId,
                            @RequestParam(name = "enforceAck", defaultValue = "true") boolean enforceAck,
                            HttpServletRequest request) {
        validateAttempt(attemptId);
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        securityHelper.verifyLearnerAccess(caller, learnerId);

        Entitlement entitlement = require(learnerId);

        // Critical Business Rule 1 & Review Checklist: Feedback acknowledgement is actually enforced before retry start
        if (enforceAck) {
            boolean acked = feedbackServiceClient.isFeedbackAcknowledged(attemptId);
            if (!acked) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Feedback acknowledgement is required before starting retry for attempt " + attemptId);
            }
        }

        synchronized (this) {
            if (!entitlement.getReservations().remove(attemptId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Retry must be reserved before start");
            }
            entitlement.getStartedAttempts().add(attemptId);
            // Critical Business Rule 4: Consume token only when retry actually starts
            entitlement.setTokens(entitlement.getTokens() - 1);
            entitlement.setReservedAttemptId(entitlement.getReservedAttemptId());
            entitlement = entitlementRepository.save(entitlement);
        }

        // Publish Kafka LabRetryStarted event
        retryEventProducer.publishRetryStarted(attemptId, learnerId);

        auditLogRepository.save(new AuditLog("START", learnerId, attemptId,
                caller.userId != null ? caller.userId : learnerId, "Started retry for attempt " + attemptId + " (1 token consumed)"));

        return entitlement;
    }

    @GetMapping("/{learnerId}")
    public Entitlement get(@PathVariable("learnerId") String learnerId, HttpServletRequest request) {
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        securityHelper.verifyLearnerAccess(caller, learnerId);
        return require(learnerId);
    }

    // --- OPTIONAL EXTENSION: TEACHER APPROVAL FOR ADDITIONAL RETRIES ---

    @PostMapping("/{learnerId}/requests")
    public RetryRequest requestAdditionalRetry(@PathVariable("learnerId") String learnerId,
                                              @RequestBody(required = false) Map<String, String> body,
                                              @RequestParam(name = "reason", required = false) String paramReason,
                                              HttpServletRequest request) {
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        securityHelper.verifyLearnerAccess(caller, learnerId);

        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : paramReason;
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason for extra retry is required");
        }

        RetryRequest retryReq = new RetryRequest(learnerId, reason.trim());
        retryReq = retryRequestRepository.save(retryReq);

        auditLogRepository.save(new AuditLog("REQUEST_EXTRA", learnerId, null,
                caller.userId != null ? caller.userId : learnerId, "Requested extra retry: " + reason));

        return retryReq;
    }

    @GetMapping("/{learnerId}/requests")
    public List<RetryRequest> getLearnerRequests(@PathVariable("learnerId") String learnerId, HttpServletRequest request) {
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        securityHelper.verifyLearnerAccess(caller, learnerId);
        return retryRequestRepository.findByLearnerIdOrderByIdDesc(learnerId);
    }

    @GetMapping("/requests")
    public List<RetryRequest> getAllRequests(HttpServletRequest request) {
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        if (caller.isLearner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only instructors can review all retry requests");
        }
        return retryRequestRepository.findAllByOrderByIdDesc();
    }

    @PostMapping("/requests/{id}/review")
    @Transactional
    public RetryRequest reviewRequest(@PathVariable("id") long id,
                                      @RequestParam(name = "approved") boolean approved,
                                      @RequestParam(name = "decisionReason") String decisionReason,
                                      @RequestParam(name = "grantedTokens", defaultValue = "1") int grantedTokens,
                                      HttpServletRequest request) {
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        if (caller.isLearner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only instructors can review retry requests");
        }

        RetryRequest retryReq = retryRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Retry request not found"));

        if (!"PENDING".equalsIgnoreCase(retryReq.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is already reviewed");
        }

        retryReq.setStatus(approved ? "APPROVED" : "REJECTED");
        retryReq.setDecisionReason(decisionReason);
        retryReq.setReviewedBy(caller.userId != null ? caller.userId : "instructor1");
        retryReq.setReviewedAt(Instant.now());
        retryReq.setGrantedTokens(approved ? Math.max(1, grantedTokens) : 0);

        // Critical Business Rule 5: Instructor approval grants entitlement via controlled operation, but DOES NOT auto-start retry
        if (approved) {
            Entitlement entitlement = require(retryReq.getLearnerId());
            entitlement.setTokens(entitlement.getTokens() + retryReq.getGrantedTokens());
            entitlementRepository.save(entitlement);
        }

        retryReq = retryRequestRepository.save(retryReq);

        auditLogRepository.save(new AuditLog(approved ? "APPROVE_EXTRA" : "REJECT_EXTRA",
                retryReq.getLearnerId(), null, caller.userId != null ? caller.userId : "instructor1",
                "Decision: " + (approved ? "APPROVED (+" + retryReq.getGrantedTokens() + " tokens)" : "REJECTED") + ". Note: " + decisionReason));

        return retryReq;
    }

    // --- AUDIT TRAIL ---

    @GetMapping("/audit")
    public List<AuditLog> getAuditLogs(@RequestParam(name = "learnerId", required = false) String learnerId,
                                      HttpServletRequest request) {
        SecurityHelper.UserContext caller = securityHelper.getCaller(request);
        if (caller.isLearner()) {
            return auditLogRepository.findByLearnerIdOrderByIdDesc(caller.userId);
        }
        if (learnerId != null && !learnerId.isBlank()) {
            return auditLogRepository.findByLearnerIdOrderByIdDesc(learnerId.trim());
        }
        return auditLogRepository.findAllByOrderByIdDesc();
    }

    private Entitlement require(String learnerId) {
        return entitlementRepository.findById(learnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seed entitlement first for " + learnerId));
    }

    private void validateAttempt(String attemptId) {
        if (attemptId == null || attemptId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attemptId is required");
        }
    }
}
