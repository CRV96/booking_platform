package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.payment.CreatePaymentIntentRequest;
import com.booking.platform.common.grpc.payment.PaymentIntentResponse;
import com.booking.platform.common.grpc.payment.PaymentServiceGrpc;
import com.booking.platform.graphql_gateway.constants.PaymentServiceConst;
import com.booking.platform.graphql_gateway.grpc.client.PaymentClient;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;

/**
 * gRPC client implementation for calling payment-service.
 * JWT is forwarded automatically by {@code JwtForwardingClientInterceptor}.
 */
@Service
@Slf4j
public class PaymentServiceClientImpl implements PaymentClient {

    @GrpcClient(PaymentServiceConst.GRPC_CLIENT)
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    @Override
    public PaymentIntentResponse createPaymentIntent(String bookingId, String amount, String currency) {
        ApplicationLogger.logMessage(log, Level.DEBUG,
                "Calling payment-service: CreatePaymentIntent bookingId='{}', amount={} {}",
                bookingId, amount, currency);

        return paymentServiceStub.createPaymentIntent(
                CreatePaymentIntentRequest.newBuilder()
                        .setBookingId(bookingId)
                        .setAmount(amount)
                        .setCurrency(currency)
                        .build());
    }
}
