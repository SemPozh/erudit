package com.erudit.payment;

import java.util.List;

public record PaymentPage(List<Payment> items, long total, int page, int size) {
}

