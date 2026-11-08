package com.unihire.repositories;

import com.unihire.models.ResumeParsingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeParsingJobRepository extends JpaRepository<ResumeParsingJob, Long> {
    List<ResumeParsingJob> findByCandidateProfileIdOrderByCreatedAtDesc(Long candidateProfileId);
    Optional<ResumeParsingJob> findTopByCandidateProfileIdOrderByCreatedAtDesc(Long candidateProfileId);
}
