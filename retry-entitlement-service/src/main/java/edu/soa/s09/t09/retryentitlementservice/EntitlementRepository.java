package edu.soa.s09.t09.retryentitlementservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntitlementRepository extends JpaRepository<Entitlement, String> {
}

