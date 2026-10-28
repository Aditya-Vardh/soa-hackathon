package edu.soa.s09.t09.labattemptservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    List<Attempt> findByLearnerIdOrderByIdDesc(String learnerId);
    List<Attempt> findAllByOrderByIdDesc();
    List<Attempt> findByLearnerIdAndLabId(String learnerId, String labId);
}

