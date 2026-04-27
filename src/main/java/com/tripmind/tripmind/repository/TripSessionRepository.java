package com.tripmind.tripmind.repository;

import com.tripmind.tripmind.entity.TripSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripSessionRepository extends JpaRepository<TripSessionEntity, Long> {
    Optional<TripSessionEntity> findByRequestId(String requestId);
    Optional<TripSessionEntity> findBySessionKey(String sessionKey);
}
