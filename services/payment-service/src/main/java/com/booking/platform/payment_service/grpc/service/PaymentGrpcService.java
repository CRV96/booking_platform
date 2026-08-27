package com.booking.platform.payment_service.grpc.service;

import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.common.grpc.payment.ConfirmMockPaymentRequest;
import com.booking.platform.common.grpc.payment.CreatePaymentIntentRequest;
import com.booking.platform.common.grpc.payment.PaymentIntentResponse;
import com.booking.platform.common.grpc.payment.PaymentServiceGrpc;
import com.booking.platform.payment_service.dto.PaymentIntentResult;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.service.PaymentService;
import com.booking.platform.payment_service.service.impl.MockPaymentConfirmationService;
import com.booking.platform.common.logging.ApplicationLogger;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.event.Level;

import java.math.BigDecimal;

/**
 * gRPC service exposing payment operations to the graphql-gateway.
 * Delegates business logic to {@link PaymentService}.
 *
 * <p>The authenticated user ID is taken from the gRPC context set by the JWT interceptor —
 * never from the request — and the amount/currency are supplied by the gateway from the
 * authoritative booking record, so the client can't dictate what it pays.
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final PaymentService paymentService;

    // Present only in mock mode (gated bean). null in stripe mode → ConfirmMockPayment is rejected.
    private final ObjectProvider<MockPaymentConfirmationService> mockConfirmationProvider;

    @Override
    public void createPaymentIntent(CreatePaymentIntentRequest request,
                                    StreamObserver<PaymentIntentResponse> responseObserver) {
        String userId = requireUserId();

        ApplicationLogger.logMessage(log, Level.INFO,
                "gRPC CreatePaymentIntent: user='{}', bookingId='{}', amount={} {}",
                userId, request.getBookingId(), request.getAmount(), request.getCurrency());

        PaymentIntentResult result = paymentService.getOrCreatePaymentIntent(
                request.getBookingId(),
                userId,
                new BigDecimal(request.getAmount()),
                request.getCurrency());

        responseObserver.onNext(toResponse(result));
        responseObserver.onCompleted();
    }

    @Override
    public void confirmMockPayment(ConfirmMockPaymentRequest request,
                                   StreamObserver<PaymentIntentResponse> responseObserver) {
        requireUserId();

        MockPaymentConfirmationService mockConfirmation = mockConfirmationProvider.getIfAvailable();
        if (mockConfirmation == null) {
            // Not in mock mode — this operation must never resolve a real payment without a charge.
            throw new IllegalStateException("Mock confirmation is unavailable — payment.gateway.type is not 'mock'");
        }

        ApplicationLogger.logMessage(log, Level.INFO,
                "gRPC ConfirmMockPayment: bookingId='{}'", request.getBookingId());

        PaymentEntity payment = mockConfirmation.confirm(request.getBookingId(), request.getCardNumber());

        responseObserver.onNext(toResponse(payment));
        responseObserver.onCompleted();
    }

    private PaymentIntentResponse toResponse(PaymentEntity payment) {
        return PaymentIntentResponse.newBuilder()
                .setPaymentId(payment.getId().toString())
                .setBookingId(payment.getBookingId())
                .setExternalPaymentId(nullSafe(payment.getExternalPaymentId()))
                .setClientSecret("")
                .setStatus(payment.getStatus().name())
                .build();
    }

    private PaymentIntentResponse toResponse(PaymentIntentResult result) {
        // proto3 string fields reject null — map nullable fields (e.g. client secret) to "".
        return PaymentIntentResponse.newBuilder()
                .setPaymentId(nullSafe(result.paymentId()))
                .setBookingId(nullSafe(result.bookingId()))
                .setExternalPaymentId(nullSafe(result.externalPaymentId()))
                .setClientSecret(nullSafe(result.clientSecret()))
                .setStatus(nullSafe(result.status()))
                .build();
    }

    private String requireUserId() {
        String userId = GrpcUserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user ID is required");
        }
        return userId;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
