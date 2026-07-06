package com.booking.platform.user_service.scheduler;

import com.booking.platform.user_service.entity.UserEntity;
import com.booking.platform.user_service.properties.KeycloakProperties;
import com.booking.platform.user_service.repository.UserRepository;
import com.booking.platform.user_service.service.KeycloakUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnverifiedUserCleanupSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private KeycloakUserService keycloakUserService;

    private final KeycloakProperties keycloakProperties =
            new KeycloakProperties("http://keycloak:8080", "booking-platform", "admin", "secret");

    private UnverifiedUserCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new UnverifiedUserCleanupScheduler(userRepository, keycloakUserService, keycloakProperties);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 7);
    }

    @Test
    void deleteUnverifiedUsers_noStaleUsers_skipsKeycloakDeletion() {
        when(userRepository.findUnverifiedUsersOlderThan(anyLong(), anyString()))
                .thenReturn(List.of());

        scheduler.deleteUnverifiedUsers();

        verifyNoInteractions(keycloakUserService);
    }

    @Test
    void deleteUnverifiedUsers_oneStaleUser_deletesIt() {
        UserEntity user = staleUser("user-1", "user1@example.com");
        when(userRepository.findUnverifiedUsersOlderThan(anyLong(), anyString()))
                .thenReturn(List.of(user));

        scheduler.deleteUnverifiedUsers();

        verify(keycloakUserService).deleteUser("user-1");
    }

    @Test
    void deleteUnverifiedUsers_multipleStaleUsers_deletesAll() {
        UserEntity user1 = staleUser("user-1", "u1@example.com");
        UserEntity user2 = staleUser("user-2", "u2@example.com");
        UserEntity user3 = staleUser("user-3", "u3@example.com");
        when(userRepository.findUnverifiedUsersOlderThan(anyLong(), anyString()))
                .thenReturn(List.of(user1, user2, user3));

        scheduler.deleteUnverifiedUsers();

        verify(keycloakUserService).deleteUser("user-1");
        verify(keycloakUserService).deleteUser("user-2");
        verify(keycloakUserService).deleteUser("user-3");
    }

    @Test
    void deleteUnverifiedUsers_oneDeleteFails_continuesWithRemainingUsers() {
        UserEntity user1 = staleUser("user-1", "u1@example.com");
        UserEntity user2 = staleUser("user-2", "u2@example.com");
        when(userRepository.findUnverifiedUsersOlderThan(anyLong(), anyString()))
                .thenReturn(List.of(user1, user2));
        doThrow(new RuntimeException("Keycloak unavailable")).when(keycloakUserService).deleteUser("user-1");

        // Must not throw even when one deletion fails
        scheduler.deleteUnverifiedUsers();

        verify(keycloakUserService).deleteUser("user-1");
        verify(keycloakUserService).deleteUser("user-2");
    }

    @Test
    void deleteUnverifiedUsers_usesRealmFromProperties() {
        when(userRepository.findUnverifiedUsersOlderThan(anyLong(), eq("booking-platform")))
                .thenReturn(List.of());

        scheduler.deleteUnverifiedUsers();

        verify(userRepository).findUnverifiedUsersOlderThan(anyLong(), eq("booking-platform"));
    }

    @Test
    void deleteUnverifiedUsers_cutoffCalculatedFromRetentionDays() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);
        when(userRepository.findUnverifiedUsersOlderThan(anyLong(), anyString()))
                .thenReturn(List.of());

        long before = System.currentTimeMillis();
        scheduler.deleteUnverifiedUsers();
        long after = System.currentTimeMillis();

        // Capture the cutoff used and verify it's roughly now - 30 days
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        org.mockito.ArgumentCaptor<Long> cutoffCaptor = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(userRepository).findUnverifiedUsersOlderThan(cutoffCaptor.capture(), anyString());
        long capturedCutoff = cutoffCaptor.getValue();
        assertThat(capturedCutoff).isBetween(before - thirtyDaysMs, after - thirtyDaysMs + 1000);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UserEntity staleUser(String id, String email) {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        return user;
    }
}
