package com.erudit.payment.model;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
@Table("subscriptions")
public record Subscription(@Id UUID id, UUID userId, String planCode, String status,
        Instant startDate, Instant endDate) {
    public boolean activeAt(Instant time) {
        return status.equals("ACTIVE") && !time.isBefore(startDate) && time.isBefore(endDate);
    }
}
