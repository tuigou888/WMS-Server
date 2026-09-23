package com.wms.config;

import com.wms.model.entity.UserAccount;
import com.wms.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductionBootstrapConfigTest {
    @Test
    void createsAdminFromExplicitBootstrapCredentials() throws Exception {
        UserAccountRepository users = mock(UserAccountRepository.class);
        when(users.findAll()).thenReturn(List.of());
        MockEnvironment env = new MockEnvironment()
                .withProperty("WMS_BOOTSTRAP_ADMIN_USERNAME", "release-admin")
                .withProperty("WMS_BOOTSTRAP_ADMIN_PASSWORD", "a-long-initial-password");

        new ProductionBootstrapConfig().bootstrapAdmin(env, users, new BCryptPasswordEncoder()).run();

        verify(users).save(argThat(user -> "release-admin".equals(user.getUsername())
                && "ADMIN".equals(user.getRole())
                && user.getPassword() != null
                && new BCryptPasswordEncoder().matches("a-long-initial-password", user.getPassword())));
    }

    @Test
    void refusesEmptyProductionDatabaseWithoutBootstrapCredentials() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        when(users.findAll()).thenReturn(List.of());
        MockEnvironment env = new MockEnvironment();

        assertThrows(IllegalStateException.class,
                () -> new ProductionBootstrapConfig().bootstrapAdmin(env, users, new BCryptPasswordEncoder()).run());
        verify(users, never()).save(any(UserAccount.class));
    }

    @Test
    void leavesExistingAdminUntouched() throws Exception {
        UserAccountRepository users = mock(UserAccountRepository.class);
        UserAccount existing = new UserAccount();
        existing.setUsername("existing-admin");
        existing.setRole("ADMIN");
        when(users.findAll()).thenReturn(List.of(existing));

        new ProductionBootstrapConfig().bootstrapAdmin(new MockEnvironment(), users, new BCryptPasswordEncoder()).run();

        verify(users, never()).save(any(UserAccount.class));
        assertTrue(existing.getRole().equals("ADMIN"));
    }
}
