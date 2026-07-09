package com.carrental.repository;

import com.carrental.entity.SubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {
    boolean existsByWhopEventId(String whopEventId);
}
