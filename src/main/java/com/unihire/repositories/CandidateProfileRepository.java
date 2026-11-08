package com.unihire.repositories;

import com.unihire.models.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {
    @Query("SELECT DISTINCT cp FROM CandidateProfile cp " +
           "LEFT JOIN cp.experiences exp " +
           "WHERE LOWER(cp.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(cp.lastName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(exp.jobTitle) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(exp.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<CandidateProfile> searchCandidatesJPA(@Param("query") String query, Pageable pageable);
}
