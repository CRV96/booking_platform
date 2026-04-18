package com.booking.platform.user_service.mapper;

import com.booking.platform.common.grpc.user.UserInfo;
import com.booking.platform.user_service.constants.UserAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserGrpcMapperTest {

    private UserGrpcMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UserGrpcMapper();
    }

    // ── Basic field mapping ───────────────────────────────────────────────────

    @Test
    void toUserInfo_mapsAllBasicFields() {
        UserInfo info = mapper.toUserInfo(fullUser(), List.of());

        assertThat(info.getId()).isEqualTo("user-123");
        assertThat(info.getUsername()).isEqualTo("john.doe");
        assertThat(info.getEmail()).isEqualTo("john@example.com");
        assertThat(info.getFirstName()).isEqualTo("John");
        assertThat(info.getLastName()).isEqualTo("Doe");
        assertThat(info.getEmailVerified()).isTrue();
        assertThat(info.getEnabled()).isTrue();
    }

    @Test
    void toUserInfo_includesAllRoles() {
        UserInfo info = mapper.toUserInfo(fullUser(), List.of("USER", "ADMIN"));

        assertThat(info.getRolesList()).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void toUserInfo_formatsTimestampAsIso8601() {
        UserRepresentation user = fullUser();
        user.setCreatedTimestamp(1_700_000_000_000L);

        UserInfo info = mapper.toUserInfo(user, List.of());

        assertThat(info.getCreatedAt())
                .isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L).toString());
    }

    @Test
    void toUserInfo_nullTimestamp_returnsEmptyCreatedAt() {
        UserRepresentation user = fullUser();
        user.setCreatedTimestamp(null);

        assertThat(mapper.toUserInfo(user, List.of()).getCreatedAt()).isEmpty();
    }

    @Test
    void toUserInfo_nullUsername_returnsEmptyString() {
        UserRepresentation user = fullUser();
        user.setUsername(null);

        assertThat(mapper.toUserInfo(user, List.of()).getUsername()).isEmpty();
    }

    @Test
    void toUserInfo_nullEmail_returnsEmptyString() {
        UserRepresentation user = fullUser();
        user.setEmail(null);

        assertThat(mapper.toUserInfo(user, List.of()).getEmail()).isEmpty();
    }

    @Test
    void toUserInfo_nullEmailVerified_defaultsFalse() {
        UserRepresentation user = fullUser();
        user.setEmailVerified(null);

        assertThat(mapper.toUserInfo(user, List.of()).getEmailVerified()).isFalse();
    }

    @Test
    void toUserInfo_nullEnabled_defaultsFalse() {
        UserRepresentation user = fullUser();
        user.setEnabled(null);

        assertThat(mapper.toUserInfo(user, List.of()).getEnabled()).isFalse();
    }

    // ── Attribute mapping ─────────────────────────────────────────────────────

    @Test
    void toUserInfo_nullAttributes_skipsMapping() {
        UserRepresentation user = fullUser();
        user.setAttributes(null);

        UserInfo info = mapper.toUserInfo(user, List.of());

        assertThat(info.getPhoneNumber()).isEmpty();
        assertThat(info.getCountry()).isEmpty();
    }

    @Test
    void toUserInfo_stringAttributes_areMapped() {
        UserRepresentation user = fullUser();
        user.setAttributes(Map.of(
                UserAttributes.PHONE_NUMBER, List.of("+40712345678"),
                UserAttributes.COUNTRY, List.of("RO"),
                UserAttributes.PREFERRED_LANGUAGE, List.of("ro"),
                UserAttributes.PREFERRED_CURRENCY, List.of("RON"),
                UserAttributes.TIMEZONE, List.of("Europe/Bucharest"),
                UserAttributes.PROFILE_PICTURE_URL, List.of("https://example.com/pic.jpg")
        ));

        UserInfo info = mapper.toUserInfo(user, List.of());

        assertThat(info.getPhoneNumber()).isEqualTo("+40712345678");
        assertThat(info.getCountry()).isEqualTo("RO");
        assertThat(info.getPreferredLanguage()).isEqualTo("ro");
        assertThat(info.getPreferredCurrency()).isEqualTo("RON");
        assertThat(info.getTimezone()).isEqualTo("Europe/Bucharest");
        assertThat(info.getProfilePictureUrl()).isEqualTo("https://example.com/pic.jpg");
    }

    @Test
    void toUserInfo_emailNotificationsTrue_isMappedAsTrue() {
        UserRepresentation user = fullUser();
        user.setAttributes(Map.of(UserAttributes.EMAIL_NOTIFICATIONS, List.of("true")));

        assertThat(mapper.toUserInfo(user, List.of()).getEmailNotifications()).isTrue();
    }

    @Test
    void toUserInfo_smsNotificationsFalse_isMappedAsFalse() {
        UserRepresentation user = fullUser();
        user.setAttributes(Map.of(UserAttributes.SMS_NOTIFICATIONS, List.of("false")));

        assertThat(mapper.toUserInfo(user, List.of()).getSmsNotifications()).isFalse();
    }

    @Test
    void toUserInfo_emptyAttributeList_skipsAttribute() {
        UserRepresentation user = fullUser();
        user.setAttributes(Map.of(UserAttributes.PHONE_NUMBER, List.of()));

        assertThat(mapper.toUserInfo(user, List.of()).getPhoneNumber()).isEmpty();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UserRepresentation fullUser() {
        UserRepresentation user = new UserRepresentation();
        user.setId("user-123");
        user.setUsername("john.doe");
        user.setEmail("john@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setCreatedTimestamp(1_700_000_000_000L);
        return user;
    }
}
