package com.unihire.repositories;

import com.unihire.models.Job;
import com.unihire.models.JobStatus;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JobSpecification {

    public static Specification<Job> searchPublicJobs(String keyword, String location, String department) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Must be PUBLISHED
            predicates.add(criteriaBuilder.equal(root.get("status"), JobStatus.PUBLISHED));

            // Expiry date must be null OR in the future
            Predicate expiryNull = criteriaBuilder.isNull(root.get("expiryDate"));
            Predicate expiryFuture = criteriaBuilder.greaterThan(root.get("expiryDate"), LocalDateTime.now());
            predicates.add(criteriaBuilder.or(expiryNull, expiryFuture));

            // Keyword in Title or Description
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                Predicate titleMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likePattern);
                Predicate descMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), likePattern);
                predicates.add(criteriaBuilder.or(titleMatch, descMatch));
            }

            // Location
            if (location != null && !location.trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("location")), "%" + location.toLowerCase() + "%"));
            }

            // Department (Exact Match)
            if (department != null && !department.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("department"), department));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
