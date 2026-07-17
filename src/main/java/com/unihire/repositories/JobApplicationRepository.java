package com.unihire.repositories;

import com.unihire.models.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByJobId(Long jobId);
    List<JobApplication> findByCandidateProfileId(Long candidateId);
    boolean existsByJobIdAndCandidateProfileId(Long jobId, Long candidateId);
}
