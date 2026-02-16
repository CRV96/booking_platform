package com.booking.platform.user_service.service;

import com.booking.platform.user_service.base.BaseIntegrationTest;
import com.booking.platform.user_service.entity.UserEntity;
import com.booking.platform.user_service.exception.user.UserNotFoundException;
import com.booking.platform.user_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link UserServiceImpl} — covers lookups by ID, username,
 * and email, plus Redis cache behaviour (hit, miss, population).
 *
 * <p>Users are inserted via {@link org.springframework.jdbc.core.JdbcTemplate} directly
 * into the {@code user_entity} table, mirroring how Keycloak writes data. JPA is used
 * only for reads, matching the production read-only pattern.
 */
@DisplayName("UserService Integration Tests")
class UserServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserServiceImpl userService;

    // =========================================================================
    // GET BY ID
    // =========================================================================

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("returns user when found by ID")
        void getUserById_found() {
            String id = insertUser("alice", "alice@example.com", "Alice", "Smith", "test-realm");

            UserEntity user = userService.getUserById(id);

            assertThat(user.getId()).isEqualTo(id);
            assertThat(user.getUsername()).isEqualTo("alice");
            assertThat(user.getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("throws UserNotFoundException when ID does not exist")
        void getUserById_notFound() {
            assertThatThrownBy(() -> userService.getUserById("non-existent-id"))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("non-existent-id");
        }

        @Test
        @DisplayName("populates user:profile cache after first call")
        void getUserById_populatesCache() {
            String id = insertUser("alice", "alice@example.com", "Alice", "Smith", "test-realm");

            userService.getUserById(id);

            Cache cache = cacheManager.getCache("user:profile");
            assertThat(cache).isNotNull();
            assertThat(cache.get(id)).isNotNull();
        }

        @Test
        @DisplayName("second call returns from Redis — not PostgreSQL")
        void getUserById_secondCallFromCache() {
            String id = insertUser("alice", "alice@example.com", "Alice", "Smith", "test-realm");

            // First call — populates cache
            userService.getUserById(id);

            // Delete from PostgreSQL to prove second call uses Redis
            jdbcTemplate.update("DELETE FROM user_entity WHERE id = ?", id);

            // Second call — must come from cache, not DB
            UserEntity fromCache = userService.getUserById(id);
            assertThat(fromCache.getId()).isEqualTo(id);
            assertThat(fromCache.getUsername()).isEqualTo("alice");
        }
    }

    // =========================================================================
    // GET BY USERNAME
    // =========================================================================

    @Nested
    @DisplayName("getUserByUsername")
    class GetUserByUsername {

        @Test
        @DisplayName("returns user when found by username")
        void getUserByUsername_found() {
            insertUser("bob", "bob@example.com", "Bob", "Jones", "test-realm");

            UserEntity user = userService.getUserByUsername("bob");

            assertThat(user.getUsername()).isEqualTo("bob");
            assertThat(user.getEmail()).isEqualTo("bob@example.com");
        }

        @Test
        @DisplayName("throws UserNotFoundException when username does not exist")
        void getUserByUsername_notFound() {
            assertThatThrownBy(() -> userService.getUserByUsername("ghost"))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("populates user:username cache after first call")
        void getUserByUsername_populatesCache() {
            insertUser("bob", "bob@example.com", "Bob", "Jones", "test-realm");

            userService.getUserByUsername("bob");

            Cache cache = cacheManager.getCache("user:username");
            assertThat(cache).isNotNull();
            assertThat(cache.get("bob")).isNotNull();
        }

        @Test
        @DisplayName("second call returns from Redis — not PostgreSQL")
        void getUserByUsername_secondCallFromCache() {
            insertUser("bob", "bob@example.com", "Bob", "Jones", "test-realm");

            userService.getUserByUsername("bob");

            // Delete from PostgreSQL
            jdbcTemplate.update("DELETE FROM user_entity WHERE username = ?", "bob");

            // Must come from cache
            UserEntity fromCache = userService.getUserByUsername("bob");
            assertThat(fromCache.getUsername()).isEqualTo("bob");
        }
    }

    // =========================================================================
    // GET BY EMAIL
    // =========================================================================

    @Nested
    @DisplayName("getUserByEmail")
    class GetUserByEmail {

        @Test
        @DisplayName("returns user when found by email")
        void getUserByEmail_found() {
            insertUser("carol", "carol@example.com", "Carol", "White", "test-realm");

            UserEntity user = userService.getUserByEmail("carol@example.com");

            assertThat(user.getEmail()).isEqualTo("carol@example.com");
            assertThat(user.getUsername()).isEqualTo("carol");
        }

        @Test
        @DisplayName("throws UserNotFoundException when email does not exist")
        void getUserByEmail_notFound() {
            assertThatThrownBy(() -> userService.getUserByEmail("nobody@example.com"))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("populates user:email cache after first call")
        void getUserByEmail_populatesCache() {
            insertUser("carol", "carol@example.com", "Carol", "White", "test-realm");

            userService.getUserByEmail("carol@example.com");

            Cache cache = cacheManager.getCache("user:email");
            assertThat(cache).isNotNull();
            assertThat(cache.get("carol@example.com")).isNotNull();
        }

        @Test
        @DisplayName("second call returns from Redis — not PostgreSQL")
        void getUserByEmail_secondCallFromCache() {
            insertUser("carol", "carol@example.com", "Carol", "White", "test-realm");

            userService.getUserByEmail("carol@example.com");

            jdbcTemplate.update("DELETE FROM user_entity WHERE email = ?", "carol@example.com");

            UserEntity fromCache = userService.getUserByEmail("carol@example.com");
            assertThat(fromCache.getEmail()).isEqualTo("carol@example.com");
        }
    }

    // =========================================================================
    // EXISTS CHECKS
    // =========================================================================

    @Nested
    @DisplayName("existsByEmail / existsByUsername")
    class ExistsChecks {

        @Test
        @DisplayName("existsByEmail returns true when email is present")
        void existsByEmail_true() {
            insertUser("dave", "dave@example.com", "Dave", "Brown", "test-realm");

            assertThat(userService.existsByEmail("dave@example.com")).isTrue();
        }

        @Test
        @DisplayName("existsByEmail returns false when email is absent")
        void existsByEmail_false() {
            assertThat(userService.existsByEmail("nobody@example.com")).isFalse();
        }

        @Test
        @DisplayName("existsByUsername returns true when username is present")
        void existsByUsername_true() {
            insertUser("dave", "dave@example.com", "Dave", "Brown", "test-realm");

            assertThat(userService.existsByUsername("dave")).isTrue();
        }

        @Test
        @DisplayName("existsByUsername returns false when username is absent")
        void existsByUsername_false() {
            assertThat(userService.existsByUsername("ghost")).isFalse();
        }
    }

    // =========================================================================
    // USER COUNT
    // =========================================================================

    @Nested
    @DisplayName("getUserCount")
    class UserCount {

        @Test
        @DisplayName("returns 0 when no users exist")
        void getUserCount_empty() {
            assertThat(userService.getUserCount()).isZero();
        }

        @Test
        @DisplayName("returns correct count after inserting users")
        void getUserCount_withUsers() {
            insertUser("u1", "u1@example.com", "User", "One", "test-realm");
            insertUser("u2", "u2@example.com", "User", "Two", "test-realm");
            insertUser("u3", "u3@example.com", "User", "Three", "test-realm");

            assertThat(userService.getUserCount()).isEqualTo(3);
        }
    }
}
