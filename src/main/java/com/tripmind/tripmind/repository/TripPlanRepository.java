package com.tripmind.tripmind.repository;

import com.tripmind.tripmind.entity.TripPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripPlanRepository extends JpaRepository<TripPlanEntity, Long> {
    Optional<TripPlanEntity> findByRequestId(String requestId);
    List<TripPlanEntity> findTop3BySessionKeyOrderByCreatedAtDesc(String sessionKey);
}
