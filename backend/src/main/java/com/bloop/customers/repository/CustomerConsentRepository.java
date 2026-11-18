package com.bloop.customers.repository;

import com.bloop.customers.entity.CustomerConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerConsentRepository extends JpaRepository<CustomerConsent, UUID> {

    List<CustomerConsent> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
