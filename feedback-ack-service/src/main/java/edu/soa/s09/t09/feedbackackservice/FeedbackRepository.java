package edu.soa.s09.t09.feedbackackservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByLearnerIdOrderByIdDesc(String learnerId);
    List<Feedback> findAllByOrderByIdDesc();
    List<Feedback> findByAttemptId(String attemptId);
    Optional<Feedback> findTopByAttemptIdOrderByIdDesc(String attemptId);
}

