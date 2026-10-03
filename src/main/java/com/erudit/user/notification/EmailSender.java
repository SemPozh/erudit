package com.erudit.user.notification;

public interface EmailSender {
    void sendVerificationEmail(String toEmail, String recipientName, String verificationLink);
}