package com.cinema.management.model.viewmodel.account;

import com.cinema.management.model.entity.booking.Booking;
import java.time.LocalDate;
import java.util.List;

/** Profile data for MVC views; excludes password hashes and verification/reset tokens. */
public record AccountProfile(Integer userId, String fullName, String email,
                             String phoneNumber, LocalDate dateOfBirth, List<Booking> bookings) { }
