package com.cinema.management.repository.payment;

import com.cinema.management.model.entity.payment.PaymentIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Integer> {
    Optional<PaymentIntent> findByPaymentReference(String paymentReference);
    Optional<PaymentIntent> findBySePayTransactionId(Long sePayTransactionId);
    Optional<PaymentIntent> findByBookingHoldBookingHoldId(UUID bookingHoldId);
    List<PaymentIntent> findByStatusAndExpiresAtUtcBefore(String status, Instant expiresAtUtc);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentIntent p where p.paymentReference = :reference")
    Optional<PaymentIntent> findByPaymentReferenceForUpdate(
            @Param("reference") String paymentReference);
}

