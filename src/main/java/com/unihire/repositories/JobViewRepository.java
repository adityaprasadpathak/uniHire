package com.unihire.repositories;

import com.unihire.models.JobView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobViewRepository extends JpaRepository<JobView, Long> {
    
    @Query("SELECT COUNT(jv) FROM JobView jv WHERE jv.job.employer.user.id = :employerId")
    long countByJobEmployerId(@Param("employerId") Long employerId);

    @Query("SELECT jv.viewedAt FROM JobView jv WHERE jv.job.employer.user.id = :employerId")
    List<LocalDateTime> findAllViewDatesByEmployerId(@Param("employerId") Long employerId);

    // Deduplication check
    Optional<JobView> findTopByJobIdAndSessionIdOrderByViewedAtDesc(Long jobId, String sessionId);
    Optional<JobView> findTopByJobIdAndViewerIdOrderByViewedAtDesc(Long jobId, Long viewerId);
}
