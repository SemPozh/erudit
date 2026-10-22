package com.erudit.user.service;

public interface EmailSender {
    void sendVerificationEmail(String toEmail, String recipientName, String verificationLink);
}
