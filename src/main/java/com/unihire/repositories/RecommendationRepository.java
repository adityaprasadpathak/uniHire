package com.unihire.repositories;

import com.unihire.models.Recommendation;
import com.unihire.models.RecommendationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByJobIdAndTypeOrderByScoreDesc(Long jobId, RecommendationType type, Pageable pageable);
    List<Recommendation> findByCandidateIdAndTypeOrderByScoreDesc(Long candidateId, RecommendationType type, Pageable pageable);
    void deleteByJobIdAndType(Long jobId, RecommendationType type);
    void deleteByCandidateIdAndType(Long candidateId, RecommendationType type);
}
