package com.erudit.payment;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration
@EnableScheduling
public class PaymentConfiguration {
    @Bean Clock paymentClock() { return Clock.systemUTC(); }
}
