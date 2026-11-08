package com.unihire.repositories;

import com.unihire.models.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByJobId(Long jobId);
    List<JobApplication> findByCandidateProfileId(Long candidateId);
    boolean existsByJobIdAndCandidateProfileId(Long jobId, Long candidateId);

    @Query("SELECT a.job.id FROM JobApplication a WHERE a.candidateProfile.id = :candidateId")
    Set<Long> findAppliedJobIdsByCandidateProfileId(@Param("candidateId") Long candidateId);
}
