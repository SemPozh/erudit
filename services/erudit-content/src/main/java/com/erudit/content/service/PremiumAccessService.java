package com.erudit.content.service;
import com.erudit.integration.InternalClients.EntitlementClient;
import java.util.UUID;
import org.springframework.stereotype.Service;
@Service
public class PremiumAccessService {
    private final EntitlementClient payments;
    public PremiumAccessService(EntitlementClient payments) { this.payments = payments; }
    public boolean premiumFor(String header) {
        try {
            UUID user = UUID.fromString(header == null || header.isBlank()
                    ? "00000000-0000-0000-0000-000000000001" : header);
            var result = payments.get(user);
            return result != null && user.equals(result.userId()) && result.premium();
        } catch (RuntimeException unavailable) {
            // Fail closed when payment service or the demo identity is unavailable.
            return false;
        }
    }
}
