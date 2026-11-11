package com.cinema.management.repository.theater;
import com.cinema.management.model.entity.theater.SeatTypePricing;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SeatTypePricingRepository extends JpaRepository<SeatTypePricing, String> {
}

