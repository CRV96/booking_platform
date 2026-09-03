package com.booking.platform.user_service.mapper;

import com.booking.platform.common.grpc.user.BillingAddress;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * (De)serializes the structured {@link BillingAddress} to/from the single {@code billingAddress}
 * Keycloak attribute, stored as compact JSON. Keeps one already-declared attribute (≤500 chars)
 * while the app passes a structured object.
 *
 * <p>Robust to legacy plain-string addresses (seed data): a non-JSON value is surfaced as
 * {@code line1} so it still displays.
 */
public final class BillingAddressCodec {

    private BillingAddressCodec() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Serializes the address to compact JSON for storage. */
    public static String toJson(BillingAddress a) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("fullName", a.getFullName());
        m.put("line1", a.getLine1());
        m.put("line2", a.getLine2());
        m.put("city", a.getCity());
        m.put("state", a.getState());
        m.put("postalCode", a.getPostalCode());
        m.put("country", a.getCountry());
        try {
            return MAPPER.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    /** Parses the stored value; a non-JSON legacy value becomes {@code line1}. */
    public static BillingAddress fromJson(String value) {
        if (value == null || value.isBlank()) {
            return BillingAddress.getDefaultInstance();
        }
        try {
            Map<String, String> m = MAPPER.readValue(value, new TypeReference<Map<String, String>>() {});
            return BillingAddress.newBuilder()
                    .setFullName(nvl(m.get("fullName")))
                    .setLine1(nvl(m.get("line1")))
                    .setLine2(nvl(m.get("line2")))
                    .setCity(nvl(m.get("city")))
                    .setState(nvl(m.get("state")))
                    .setPostalCode(nvl(m.get("postalCode")))
                    .setCountry(nvl(m.get("country")))
                    .build();
        } catch (Exception e) {
            return BillingAddress.newBuilder().setLine1(value).build();
        }
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
