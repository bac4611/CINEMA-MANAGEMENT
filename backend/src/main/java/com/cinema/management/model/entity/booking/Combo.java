package com.cinema.management.model.entity.booking;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Combos")
@Getter @Setter @NoArgsConstructor
public class Combo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ComboID")
    private Integer comboId;
    @NotBlank(message = "Tên món không được để trống.")
    @Size(max = 150, message = "Tên món tối đa 150 ký tự.")
    private String comboName = "";
    @DecimalMin(value = "0.01", message = "Giá món phải lớn hơn 0.")
    @Column(precision = 10, scale = 2)
    private BigDecimal comboPrice = BigDecimal.ZERO;
    @Min(value = 0, message = "Số lượng không được nhỏ hơn 0.")
    private int quantity;
    @Min(0)
    private int reservedQuantity;

    @OneToMany(mappedBy = "combo")
    private List<BookingCombo> bookingCombos = new ArrayList<>();
}

