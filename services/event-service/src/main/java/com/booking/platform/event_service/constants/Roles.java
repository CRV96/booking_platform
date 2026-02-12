package com.booking.platform.event_service.constants;

/**
 * Keycloak realm role names used for authorization checks in gRPC service methods.
 */
public interface Roles {


    String EMPLOYEE = "employee";
    String CUSTOMER = "customer";
    String ADMIN    = "admin";

}
