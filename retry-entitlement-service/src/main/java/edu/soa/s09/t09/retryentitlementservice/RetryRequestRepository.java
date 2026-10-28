package edu.soa.s09.t09.retryentitlementservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RetryRequestRepository extends JpaRepository<RetryRequest, Long> {
    List<RetryRequest> findByLearnerIdOrderByIdDesc(String learnerId);
    List<RetryRequest> findAllByOrderByIdDesc();
    List<RetryRequest> findByStatusOrderByIdDesc(String status);
}

