package com.steadybit.testcontainers.example.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Modifying
    @Query("Update Order set published = :now where id = :id")
    void markAsPublished(Long id, Instant now);


    @Query("Select o from Order o where published is null")
    List<Order> findPublishPending();
}
