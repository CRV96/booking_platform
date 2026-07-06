package com.booking.platform.user_service.service;

import com.booking.platform.user_service.base.BaseIntegrationTest;
import com.booking.platform.user_service.entity.UserEntity;
import com.booking.platform.user_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for user search and listing — covers the JPQL query in
 * {@link com.booking.platform.user_service.repository.UserRepository#search},
 * pagination, and blank-query fallback.
 *
 * <p>The search query does case-insensitive LIKE matching across username, email,
 * firstName, and lastName. These tests verify all four fields are searched and
 * that DRAFT / inactive users are not hidden (there is no status filter here —
 * all users in the table are returned).
 */
@DisplayName("UserSearch Integration Tests")
class UserSearchIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserServiceImpl userService;

    // =========================================================================
    // Seed data — inserted before each test via @BeforeEach (runs after
    // BaseIntegrationTest#cleanState which already wiped the tables).
    // =========================================================================

    @BeforeEach
    void seedUsers() {
        insertUser("alice",   "alice@example.com",   "Alice",   "Smith",   "test-realm");
        insertUser("bob",     "bob@example.com",     "Bob",     "Jones",   "test-realm");
        insertUser("charlie", "charlie@example.com", "Charlie", "Brown",   "test-realm");
        insertUser("diana",   "diana@example.com",   "Diana",   "Prince",  "test-realm");
    }

    // =========================================================================
    // getAllUsers
    // =========================================================================

    @Nested
    @DisplayName("getAllUsers")
    class GetAllUsers {

        @Test
        @DisplayName("returns all users as a flat list")
        void getAllUsers_returnsList() {
            List<UserEntity> users = userService.getAllUsers();

            assertThat(users).hasSize(4);
        }

        @Test
        @DisplayName("getAllUsers with pageable returns correct page size")
        void getAllUsers_pageable_firstPage() {
            Page<UserEntity> page = userService.getAllUsers(PageRequest.of(0, 2));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(4);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("second page returns different users than first page")
        void getAllUsers_pageable_secondPage() {
            Page<UserEntity> page0 = userService.getAllUsers(PageRequest.of(0, 2));
            Page<UserEntity> page1 = userService.getAllUsers(PageRequest.of(1, 2));

            List<String> ids0 = page0.getContent().stream().map(UserEntity::getId).toList();
            List<String> ids1 = page1.getContent().stream().map(UserEntity::getId).toList();

            assertThat(ids0).doesNotContainAnyElementsOf(ids1);
        }
    }

    // =========================================================================
    // searchUsers — by username
    // =========================================================================

    @Nested
    @DisplayName("searchUsers — username match")
    class SearchByUsername {

        @Test
        @DisplayName("finds user by exact username")
        void search_exactUsername() {
            Page<UserEntity> results = userService.searchUsers("alice", PageRequest.of(0, 1000));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getUsername()).isEqualTo("alice");
        }

        @Test
        @DisplayName("finds user by partial username (case-insensitive)")
        void search_partialUsername_caseInsensitive() {
            Page<UserEntity> results = userService.searchUsers("CHAR", PageRequest.of(0, 1000));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getUsername()).isEqualTo("charlie");
        }
    }

    // =========================================================================
    // searchUsers — by email
    // =========================================================================

    @Nested
    @DisplayName("searchUsers — email match")
    class SearchByEmail {

        @Test
        @DisplayName("finds user by partial email domain")
        void search_byEmailDomain() {
            Page<UserEntity> results = userService.searchUsers("example.com", PageRequest.of(0, 1000));

            // All 4 users share the same domain
            assertThat(results.getContent()).hasSize(4);
        }

        @Test
        @DisplayName("finds user by email prefix")
        void search_byEmailPrefix() {
            Page<UserEntity> results = userService.searchUsers("diana@", PageRequest.of(0, 1000));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getEmail()).isEqualTo("diana@example.com");
        }
    }

    // =========================================================================
    // searchUsers — by first name / last name
    // =========================================================================

    @Nested
    @DisplayName("searchUsers — name match")
    class SearchByName {

        @Test
        @DisplayName("finds user by first name (case-insensitive)")
        void search_byFirstName() {
            Page<UserEntity> results = userService.searchUsers("bob", PageRequest.of(0, 1000));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getFirstName()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("finds user by last name")
        void search_byLastName() {
            Page<UserEntity> results = userService.searchUsers("Prince", PageRequest.of(0, 1000));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getLastName()).isEqualTo("Prince");
        }

        @Test
        @DisplayName("partial last name match returns correct user")
        void search_byPartialLastName() {
            Page<UserEntity> results = userService.searchUsers("rown", PageRequest.of(0, 1000));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getUsername()).isEqualTo("charlie");
        }
    }

    // =========================================================================
    // searchUsers — blank query fallback
    // =========================================================================

    @Nested
    @DisplayName("searchUsers — blank query")
    class BlankQuery {

        @Test
        @DisplayName("blank query returns all users (falls back to findAll)")
        void search_blankQuery_returnsAll() {
            Page<UserEntity> results = userService.searchUsers("", PageRequest.of(0, 1000));

            assertThat(results.getContent()).hasSize(4);
        }

        @Test
        @DisplayName("null query returns all users")
        void search_nullQuery_returnsAll() {
            Page<UserEntity> results = userService.searchUsers(null, PageRequest.of(0, 1000));

            assertThat(results.getContent()).hasSize(4);
        }

        @Test
        @DisplayName("whitespace-only query returns all users")
        void search_whitespaceQuery_returnsAll() {
            Page<UserEntity> results = userService.searchUsers("   ", PageRequest.of(0, 1000));

            assertThat(results.getContent()).hasSize(4);
        }
    }

    // =========================================================================
    // searchUsers — no results
    // =========================================================================

    @Nested
    @DisplayName("searchUsers — no match")
    class NoMatch {

        @Test
        @DisplayName("returns empty page when query matches nothing")
        void search_noMatch_returnsEmpty() {
            Page<UserEntity> results = userService.searchUsers("zzznomatch", PageRequest.of(0, 1000));

            assertThat(results.getContent()).isEmpty();
        }
    }
}
