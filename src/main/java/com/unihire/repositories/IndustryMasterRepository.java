package com.unihire.repositories;

import com.unihire.models.IndustryMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IndustryMasterRepository extends JpaRepository<IndustryMaster, Long> {
    Optional<IndustryMaster> findByIndustryName(String industryName);
}
