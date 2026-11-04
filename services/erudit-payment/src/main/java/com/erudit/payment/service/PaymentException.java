package com.erudit.payment.service;
public class PaymentException extends RuntimeException {
    private final int status;
    public PaymentException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
