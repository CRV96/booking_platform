package com.booking.platform.user_service.service;

import com.booking.platform.user_service.base.BaseIntegrationTest;
import com.booking.platform.user_service.constants.UserAttributes;
import com.booking.platform.user_service.entity.UserAttributeEntity;
import com.booking.platform.user_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for user attribute retrieval — covers
 * {@link UserServiceImpl#getUserAttributes} and {@link UserServiceImpl#getUserAttribute}.
 *
 * <p>Attributes are stored in the {@code user_attribute} table (one row per attribute),
 * inserted via JdbcTemplate to mirror Keycloak's write path.
 */
@DisplayName("UserAttribute Integration Tests")
class UserAttributeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserServiceImpl userService;

    // =========================================================================
    // getUserAttributes — all attributes for a user
    // =========================================================================

    @Nested
    @DisplayName("getUserAttributes")
    class GetUserAttributes {

        @Test
        @DisplayName("returns all attributes for a user")
        void getUserAttributes_returnsAll() {
            String userId = insertUser("alice", "alice@example.com", "Alice", "Smith", "test-realm");
            insertAttribute(userId, UserAttributes.PHONE_NUMBER, "+40700000001");
            insertAttribute(userId, UserAttributes.COUNTRY, "Romania");
            insertAttribute(userId, UserAttributes.PREFERRED_LANGUAGE, "en");

            List<UserAttributeEntity> attrs = userService.getUserAttributes(userId);

            assertThat(attrs).hasSize(3);
            assertThat(attrs).extracting(UserAttributeEntity::getName)
                    .containsExactlyInAnyOrder(
                            UserAttributes.PHONE_NUMBER,
                            UserAttributes.COUNTRY,
                            UserAttributes.PREFERRED_LANGUAGE
                    );
        }

        @Test
        @DisplayName("returns empty list when user has no attributes")
        void getUserAttributes_noAttributes() {
            String userId = insertUser("bob", "bob@example.com", "Bob", "Jones", "test-realm");

            List<UserAttributeEntity> attrs = userService.getUserAttributes(userId);

            assertThat(attrs).isEmpty();
        }

        @Test
        @DisplayName("returns only attributes belonging to the requested user")
        void getUserAttributes_isolatedPerUser() {
            String aliceId = insertUser("alice", "alice@example.com", "Alice", "Smith", "test-realm");
            String bobId   = insertUser("bob",   "bob@example.com",   "Bob",   "Jones", "test-realm");

            insertAttribute(aliceId, UserAttributes.COUNTRY, "Romania");
            insertAttribute(bobId,   UserAttributes.COUNTRY, "UK");

            List<UserAttributeEntity> aliceAttrs = userService.getUserAttributes(aliceId);
            List<UserAttributeEntity> bobAttrs   = userService.getUserAttributes(bobId);

            assertThat(aliceAttrs).hasSize(1);
            assertThat(aliceAttrs.get(0).getValue()).isEqualTo("Romania");

            assertThat(bobAttrs).hasSize(1);
            assertThat(bobAttrs.get(0).getValue()).isEqualTo("UK");
        }
    }

    // =========================================================================
    // getUserAttribute — single attribute by name
    // =========================================================================

    @Nested
    @DisplayName("getUserAttribute")
    class GetUserAttribute {

        @Test
        @DisplayName("returns the specific attribute when it exists")
        void getUserAttribute_found() {
            String userId = insertUser("carol", "carol@example.com", "Carol", "White", "test-realm");
            insertAttribute(userId, UserAttributes.TIMEZONE, "Europe/Bucharest");
            insertAttribute(userId, UserAttributes.PREFERRED_CURRENCY, "RON");

            List<UserAttributeEntity> result =
                    userService.getUserAttribute(userId, UserAttributes.TIMEZONE);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo(UserAttributes.TIMEZONE);
            assertThat(result.get(0).getValue()).isEqualTo("Europe/Bucharest");
        }

        @Test
        @DisplayName("returns empty list when the named attribute does not exist")
        void getUserAttribute_notFound() {
            String userId = insertUser("carol", "carol@example.com", "Carol", "White", "test-realm");
            insertAttribute(userId, UserAttributes.COUNTRY, "Romania");

            List<UserAttributeEntity> result =
                    userService.getUserAttribute(userId, UserAttributes.TIMEZONE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when user has no attributes at all")
        void getUserAttribute_noAttributesForUser() {
            String userId = insertUser("dave", "dave@example.com", "Dave", "Brown", "test-realm");

            List<UserAttributeEntity> result =
                    userService.getUserAttribute(userId, UserAttributes.PHONE_NUMBER);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns correct value for boolean-typed attribute")
        void getUserAttribute_booleanValue() {
            String userId = insertUser("eve", "eve@example.com", "Eve", "Green", "test-realm");
            insertAttribute(userId, UserAttributes.EMAIL_NOTIFICATIONS, "true");

            List<UserAttributeEntity> result =
                    userService.getUserAttribute(userId, UserAttributes.EMAIL_NOTIFICATIONS);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualTo("true");
        }
    }
}
