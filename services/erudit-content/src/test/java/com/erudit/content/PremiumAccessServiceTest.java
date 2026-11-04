package com.erudit.content;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.erudit.integration.InternalClients.EntitlementClient;
import com.erudit.integration.InternalContracts.Entitlement;
import com.erudit.content.service.PremiumAccessService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class PremiumAccessServiceTest {
    @Test void usesOnlyRemoteEntitlementAndFailsClosed() {
        var client = mock(EntitlementClient.class); var service = new PremiumAccessService(client);
        var user = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(client.get(user)).thenReturn(new Entitlement(user,true,null));
        assertThat(service.premiumFor(null)).isTrue();
        when(client.get(user)).thenReturn(new Entitlement(user,false,null));
        assertThat(service.premiumFor(user.toString())).isFalse();
        when(client.get(user)).thenReturn(new Entitlement(UUID.randomUUID(),true,null));
        assertThat(service.premiumFor(user.toString())).isFalse();
        when(client.get(user)).thenReturn(null);
        assertThat(service.premiumFor(user.toString())).isFalse();
        when(client.get(user)).thenThrow(new IllegalStateException("offline"));
        assertThat(service.premiumFor(user.toString())).isFalse();
        assertThat(service.premiumFor("invalid")).isFalse();
    }
}
