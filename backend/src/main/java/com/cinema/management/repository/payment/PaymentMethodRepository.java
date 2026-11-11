package com.cinema.management.repository.payment;
import com.cinema.management.model.entity.payment.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Integer> {
    Optional<PaymentMethod> findByMethodNameIgnoreCase(String methodName);
}

