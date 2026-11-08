package com.unihire.repositories;

import com.unihire.models.Job;
import com.unihire.models.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

import org.springframework.data.jpa.repository.Query;

@Repository
public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {
    List<Job> findByEmployerId(Long employerId);
    List<Job> findByEmployerIdAndStatus(Long employerId, JobStatus status);
    Page<Job> findByEmployerIdAndStatus(Long employerId, JobStatus status, Pageable pageable);
    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    @Query("SELECT j FROM Job j JOIN FETCH j.employer e JOIN FETCH e.user u")
    List<Job> findAllEager();

    @Query(value = "SELECT j FROM Job j JOIN FETCH j.employer e JOIN FETCH e.user u",
           countQuery = "SELECT count(j) FROM Job j")
    Page<Job> findAllEager(Pageable pageable);
}
