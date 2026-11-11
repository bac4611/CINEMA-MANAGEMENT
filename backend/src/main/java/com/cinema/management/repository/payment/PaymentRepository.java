package com.cinema.management.repository.payment;

import com.cinema.management.model.entity.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    List<Payment> findByBookingBookingIdOrderByPaymentDateDesc(Integer bookingId);
    List<Payment> findByStatusAndPaymentDateBetween(
            String status, LocalDateTime from, LocalDateTime to);
}

