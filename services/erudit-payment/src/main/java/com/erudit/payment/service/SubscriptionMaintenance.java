package com.erudit.payment.service;
import com.erudit.payment.repository.PaymentRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class SubscriptionMaintenance {
    private final PaymentRepository repository;
    private final Clock clock;
    public SubscriptionMaintenance(PaymentRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    @Scheduled(fixedDelayString="${erudit.payments.maintenance-interval:PT1H}", initialDelayString="${erudit.payments.maintenance-initial-delay:PT1M}")
    @Transactional
    public void expire() { repository.expireAll(clock.instant()); }
}
