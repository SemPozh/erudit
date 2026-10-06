// LoggingEmailSender.java
package com.erudit.user.notification;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingEmailSender implements EmailSender {
    @Override
    public void sendVerificationEmail(String toEmail, String recipientName, String verificationLink) {
        log.info("Verification email -> {} ({}): {}", toEmail, recipientName, verificationLink);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String recipientName, String resetLink) {
        log.info("Password reset email -> {} ({}): {}", toEmail, recipientName, resetLink);
    }
}
