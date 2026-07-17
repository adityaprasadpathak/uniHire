package com.unihire.repositories;

import com.unihire.models.Job;
import com.unihire.models.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {
    List<Job> findByEmployerId(Long employerId);
    List<Job> findByEmployerIdAndStatus(Long employerId, JobStatus status);
}
