package com.booking.platform.user_service.mapper;

import com.booking.platform.common.grpc.user.RegisterRequest;
import com.booking.platform.common.grpc.user.UpdateUserRequest;
import com.booking.platform.user_service.constants.UserAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeMapperTest {

    private AttributeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AttributeMapper();
    }

    // ── fromRegisterRequest ───────────────────────────────────────────────────

    @Test
    void fromRegisterRequest_allOptionalPresent_mapsPhoneCountryLanguage() {
        RegisterRequest request = RegisterRequest.newBuilder()
                .setEmail("a@b.com").setPassword("pass").setFirstName("A").setLastName("B")
                .setPhoneNumber("+40712345678")
                .setCountry("RO")
                .setPreferredLanguage("ro")
                .build();

        Map<String, String> attrs = mapper.fromRegisterRequest(request);

        assertThat(attrs)
                .containsEntry(UserAttributes.PHONE_NUMBER, "+40712345678")
                .containsEntry(UserAttributes.COUNTRY, "RO")
                .containsEntry(UserAttributes.PREFERRED_LANGUAGE, "ro");
    }

    @Test
    void fromRegisterRequest_noOptionalFields_returnsEmptyMap() {
        RegisterRequest request = RegisterRequest.newBuilder()
                .setEmail("a@b.com").setPassword("pass").setFirstName("A").setLastName("B")
                .build();

        assertThat(mapper.fromRegisterRequest(request)).isEmpty();
    }

    @Test
    void fromRegisterRequest_onlyPhone_mapsOnlyPhone() {
        RegisterRequest request = RegisterRequest.newBuilder()
                .setEmail("a@b.com").setPassword("pass").setFirstName("A").setLastName("B")
                .setPhoneNumber("+40712345678")
                .build();

        Map<String, String> attrs = mapper.fromRegisterRequest(request);

        assertThat(attrs).containsOnlyKeys(UserAttributes.PHONE_NUMBER);
    }

    @Test
    void fromRegisterRequest_doesNotMapUpdateOnlyAttributes() {
        RegisterRequest request = RegisterRequest.newBuilder()
                .setEmail("a@b.com").setPassword("pass").setFirstName("A").setLastName("B")
                .build();

        Map<String, String> attrs = mapper.fromRegisterRequest(request);

        assertThat(attrs).doesNotContainKey(UserAttributes.PREFERRED_CURRENCY)
                .doesNotContainKey(UserAttributes.TIMEZONE)
                .doesNotContainKey(UserAttributes.PROFILE_PICTURE_URL);
    }

    // ── fromUpdateRequest ─────────────────────────────────────────────────────

    @Test
    void fromUpdateRequest_allOptionalPresent_mapsAllEightAttributes() {
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId("user-123")
                .setPhoneNumber("+40712345678")
                .setCountry("RO")
                .setPreferredLanguage("ro")
                .setPreferredCurrency("RON")
                .setTimezone("Europe/Bucharest")
                .setProfilePictureUrl("https://example.com/pic.jpg")
                .setEmailNotifications(true)
                .setSmsNotifications(false)
                .build();

        Map<String, String> attrs = mapper.fromUpdateRequest(request);

        assertThat(attrs).hasSize(8)
                .containsEntry(UserAttributes.PHONE_NUMBER, "+40712345678")
                .containsEntry(UserAttributes.COUNTRY, "RO")
                .containsEntry(UserAttributes.PREFERRED_LANGUAGE, "ro")
                .containsEntry(UserAttributes.PREFERRED_CURRENCY, "RON")
                .containsEntry(UserAttributes.TIMEZONE, "Europe/Bucharest")
                .containsEntry(UserAttributes.PROFILE_PICTURE_URL, "https://example.com/pic.jpg")
                .containsEntry(UserAttributes.EMAIL_NOTIFICATIONS, "true")
                .containsEntry(UserAttributes.SMS_NOTIFICATIONS, "false");
    }

    @Test
    void fromUpdateRequest_noOptionalFields_returnsEmptyMap() {
        UpdateUserRequest request = UpdateUserRequest.newBuilder().setUserId("user-123").build();

        assertThat(mapper.fromUpdateRequest(request)).isEmpty();
    }

    @Test
    void fromUpdateRequest_emailNotificationsTrue_convertsToTrueString() {
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId("u").setEmailNotifications(true).build();

        assertThat(mapper.fromUpdateRequest(request))
                .containsEntry(UserAttributes.EMAIL_NOTIFICATIONS, "true");
    }

    @Test
    void fromUpdateRequest_smsNotificationsFalse_convertsToFalseString() {
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId("u").setSmsNotifications(false).build();

        assertThat(mapper.fromUpdateRequest(request))
                .containsEntry(UserAttributes.SMS_NOTIFICATIONS, "false");
    }

    @Test
    void fromUpdateRequest_onlyTimezone_mapsOnlyTimezone() {
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
                .setUserId("u").setTimezone("UTC").build();

        Map<String, String> attrs = mapper.fromUpdateRequest(request);
        assertThat(attrs).containsOnlyKeys(UserAttributes.TIMEZONE);
    }
}
