package com.booking.platform.booking_service.constants;

/**
 * Request field names used in validation and parsing error messages, so the same
 * literal is never repeated across call sites.
 */
public final class FieldConst {
    private FieldConst() {}

    public static final String EVENT_ID = "event_id";
    public static final String EVENT_TITLE = "event_title";
    public static final String SEAT_CATEGORY = "seat_category";
    public static final String CURRENCY = "currency";
    public static final String QUANTITY = "quantity";
    public static final String UNIT_PRICE = "unit_price";
    public static final String CART_ITEM_ID = "cart_item_id";
}
