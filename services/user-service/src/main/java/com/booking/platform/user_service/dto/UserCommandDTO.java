package com.booking.platform.user_service.dto;

import java.util.Map;

/**
 * Command object carrying the fields needed to create or update a user via
 * {@code KeycloakUserService}. Not every field is used by every operation:
 *
 * <ul>
 *   <li><b>Create</b> uses {@code email}, {@code password}, {@code firstName},
 *       {@code lastName}, {@code role}, {@code attributes} ({@code userId} is {@code null}).</li>
 *   <li><b>Update</b> uses {@code userId}, {@code firstName}, {@code lastName},
 *       {@code email}, {@code attributes} ({@code password} and {@code role} are {@code null}).
 *       {@code firstName}/{@code lastName}/{@code email} may be {@code null} to leave a field unchanged.</li>
 * </ul>
 *
 * @param userId     the Keycloak user ID (update only; {@code null} on create)
 * @param email      the user's email (also used as username on create)
 * @param password   the initial password (create only)
 * @param firstName  the user's first name (may be {@code null} on update to leave unchanged)
 * @param lastName   the user's last name (may be {@code null} on update to leave unchanged)
 * @param role       the realm role / group to assign, e.g. {@code "customer"} (create only)
 * @param attributes additional Keycloak user attributes (phone, country, …)
 */
public record UserCommandDTO(
        String userId,
        String email,
        String password,
        String firstName,
        String lastName,
        String role,
        Map<String, String> attributes) {
}
