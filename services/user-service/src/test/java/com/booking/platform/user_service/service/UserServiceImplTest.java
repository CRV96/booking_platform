package com.booking.platform.user_service.service;

import com.booking.platform.user_service.entity.UserAttributeEntity;
import com.booking.platform.user_service.entity.UserEntity;
import com.booking.platform.user_service.exception.user.UserNotFoundException;
import com.booking.platform.user_service.repository.UserAttributeRepository;
import com.booking.platform.user_service.repository.UserRepository;
import com.booking.platform.user_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAttributeRepository userAttributeRepository;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, userAttributeRepository);
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    void getUserById_found_returnsEntity() {
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        assertThat(service.getUserById("u1")).isSameAs(user);
    }

    @Test
    void getUserById_notFound_throwsUserNotFoundException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserById("missing"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // ── getUserByUsername ─────────────────────────────────────────────────────

    @Test
    void getUserByUsername_found_returnsEntity() {
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        assertThat(service.getUserByUsername("john")).isSameAs(user);
    }

    @Test
    void getUserByUsername_notFound_throwsUserNotFoundException() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserByUsername("missing"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // ── getUserByEmail ────────────────────────────────────────────────────────

    @Test
    void getUserByEmail_found_returnsEntity() {
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        assertThat(service.getUserByEmail("a@b.com")).isSameAs(user);
    }

    @Test
    void getUserByEmail_notFound_throwsUserNotFoundException() {
        when(userRepository.findByEmail("x@y.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserByEmail("x@y.com"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("x@y.com");
    }

    // ── searchUsers ───────────────────────────────────────────────────────────

    @Test
    void searchUsers_nullQuery_callsFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        service.searchUsers(null, pageable);

        verify(userRepository).findAll(pageable);
        verify(userRepository, never()).search(any(), any());
    }

    @Test
    void searchUsers_blankQuery_callsFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        service.searchUsers("   ", pageable);

        verify(userRepository).findAll(pageable);
        verify(userRepository, never()).search(any(), any());
    }

    @Test
    void searchUsers_nonBlankQuery_callsSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.search("john", pageable)).thenReturn(new PageImpl<>(List.of()));

        service.searchUsers("john", pageable);

        verify(userRepository).search("john", pageable);
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void searchUsers_queryWithLeadingTrailingSpaces_trimsBeforeSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.search("john", pageable)).thenReturn(new PageImpl<>(List.of()));

        service.searchUsers("  john  ", pageable);

        verify(userRepository).search("john", pageable);
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    void getAllUsers_returnsRepositoryList() {
        UserEntity u = mock(UserEntity.class);
        when(userRepository.findAll()).thenReturn(List.of(u));

        assertThat(service.getAllUsers()).containsExactly(u);
    }

    @Test
    void getAllUsers_paginated_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<UserEntity> page = new PageImpl<>(List.of());
        when(userRepository.findAll(pageable)).thenReturn(page);

        assertThat(service.getAllUsers(pageable)).isSameAs(page);
    }

    // ── getUserCount ──────────────────────────────────────────────────────────

    @Test
    void getUserCount_returnsRepositoryCount() {
        when(userRepository.count()).thenReturn(42L);

        assertThat(service.getUserCount()).isEqualTo(42L);
    }

    // ── existsByEmail / existsByUsername ──────────────────────────────────────

    @Test
    void existsByEmail_delegatesToRepository() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        assertThat(service.existsByEmail("a@b.com")).isTrue();
    }

    @Test
    void existsByUsername_delegatesToRepository() {
        when(userRepository.existsByUsername("john")).thenReturn(false);

        assertThat(service.existsByUsername("john")).isFalse();
    }

    // ── getUserAttributes ─────────────────────────────────────────────────────

    @Test
    void getUserAttributes_delegatesToRepository() {
        UserAttributeEntity attr = mock(UserAttributeEntity.class);
        when(userAttributeRepository.findByUserId("u1")).thenReturn(List.of(attr));

        assertThat(service.getUserAttributes("u1")).containsExactly(attr);
    }

    @Test
    void getUserAttribute_delegatesToRepository() {
        UserAttributeEntity attr = mock(UserAttributeEntity.class);
        when(userAttributeRepository.findByUserIdAndName("u1", "phone")).thenReturn(List.of(attr));

        assertThat(service.getUserAttribute("u1", "phone")).containsExactly(attr);
    }
}
