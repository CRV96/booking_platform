package com.booking.platform.payment_service.dto;

/**
 * Response from the payment gateway after creating a refund.
 *
 * @param refundId gateway's own refund identifier (e.g. Stripe Refund ID: "re_1234...")
 * @param status   gateway-reported refund status (e.g. "succeeded", "pending")
 */
public record GatewayRefundResponse(
        String refundId,
        String status
) {}
