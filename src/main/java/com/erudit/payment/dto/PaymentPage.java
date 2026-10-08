package com.erudit.payment.dto;

import com.erudit.payment.model.Payment;

import java.util.List;

public record PaymentPage(List<Payment> items, long total, int page, int size) {
}

