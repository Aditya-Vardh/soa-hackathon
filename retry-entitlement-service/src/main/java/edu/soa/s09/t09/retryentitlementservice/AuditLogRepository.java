package edu.soa.s09.t09.retryentitlementservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByIdDesc();
    List<AuditLog> findByLearnerIdOrderByIdDesc(String learnerId);
}

