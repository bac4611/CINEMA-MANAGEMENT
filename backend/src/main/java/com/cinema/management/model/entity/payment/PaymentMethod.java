package com.cinema.management.model.entity.payment;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PaymentMethods")
@Getter @Setter @NoArgsConstructor
public class PaymentMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MethodID")
    private Integer methodId;
    @NotBlank @Size(max = 100)
    private String methodName = "";

    @OneToMany(mappedBy = "paymentMethod")
    private List<Payment> payments = new ArrayList<>();
}

