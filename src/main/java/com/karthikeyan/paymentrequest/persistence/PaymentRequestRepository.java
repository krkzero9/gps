package com.karthikeyan.paymentrequest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequestEntity, UUID> {
}