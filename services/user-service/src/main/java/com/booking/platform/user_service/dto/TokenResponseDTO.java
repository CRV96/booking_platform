package com.booking.platform.user_service.dto;

public record TokenResponseDTO(
        String access_token,
        String refresh_token,
        int expires_in,
        int refresh_expires_in,
        String token_type
) {
}
