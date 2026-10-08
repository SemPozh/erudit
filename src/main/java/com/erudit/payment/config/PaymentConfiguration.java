package com.erudit.payment.config;

import com.erudit.payment.service.MobileReceiptVerifier;
import com.erudit.payment.service.MockMobileReceiptVerifier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfiguration {
    @Bean MobileReceiptVerifier appStoreReceiptVerifier() {
        return new MockMobileReceiptVerifier("APP_STORE");
    }

    @Bean MobileReceiptVerifier googlePlayReceiptVerifier() {
        return new MockMobileReceiptVerifier("GOOGLE_PLAY");
    }
}

