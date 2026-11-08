package com.unihire.repositories;

import com.unihire.models.CandidateResumeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidateResumeProfileRepository extends JpaRepository<CandidateResumeProfile, Long> {
}
