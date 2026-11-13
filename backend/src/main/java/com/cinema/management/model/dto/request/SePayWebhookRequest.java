package com.cinema.management.model.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
public class SePayWebhookRequest {
    private long id;
    private String gateway;
    private String transactionDate;
    private String accountNumber;
    private String subAccount;
    private String code;
    private String content;
    private String transferType;
    private String description;
    private BigDecimal transferAmount = BigDecimal.ZERO;
    private BigDecimal accumulated = BigDecimal.ZERO;
    private String referenceCode;
}
