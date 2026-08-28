package com.booking.platform.payment_service.grpc.service;

import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.common.grpc.payment.ConfirmMockPaymentRequest;
import com.booking.platform.common.grpc.payment.CreateOrderPaymentIntentRequest;
import com.booking.platform.common.grpc.payment.PaymentIntentResponse;
import com.booking.platform.payment_service.dto.PaymentIntentResult;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.service.PaymentService;
import com.booking.platform.payment_service.service.impl.MockPaymentConfirmationService;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentGrpcServiceTest {

    @Mock private PaymentService paymentService;
    @Mock private ObjectProvider<MockPaymentConfirmationService> mockConfirmationProvider;
    @Mock private MockPaymentConfirmationService mockConfirmationService;
    @Mock private StreamObserver<PaymentIntentResponse> responseObserver;

    private PaymentGrpcService service;

    private static final String USER_ID = "user-1";
    private static final String ORDER_ID = "order-1";

    @BeforeEach
    void setup() {
        service = new PaymentGrpcService(paymentService, mockConfirmationProvider);
        ReflectionTestUtils.setField(service, "gatewayType", "stripe");
        ReflectionTestUtils.setField(service, "stripePublishableKey", "pk_test_123");
    }

    /** Runs the given action with the authenticated user id in the gRPC context. */
    private void withUser(Runnable action) {
        Context.current().withValue(GrpcUserContext.USER_ID, USER_ID).run(action);
    }

    private CreateOrderPaymentIntentRequest orderRequest() {
        return CreateOrderPaymentIntentRequest.newBuilder()
                .setOrderId(ORDER_ID)
                .addAllBookingIds(List.of("b1", "b2"))
                .setAmount("50.00")
                .setCurrency("USD")
                .build();
    }

    @Test
    void createOrderPaymentIntent_delegatesAndMapsResponse_stripeMode() {
        when(paymentService.getOrCreateOrderPaymentIntent(eq(ORDER_ID), eq(USER_ID), anyList(), any(), eq("USD")))
                .thenReturn(new PaymentIntentResult("pay-1", ORDER_ID, "pi_1", "secret_1", "PROCESSING"));

        withUser(() -> service.createOrderPaymentIntent(orderRequest(), responseObserver));

        ArgumentCaptor<PaymentIntentResponse> captor = ArgumentCaptor.forClass(PaymentIntentResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();
        PaymentIntentResponse response = captor.getValue();
        assertThat(response.getPaymentId()).isEqualTo("pay-1");
        assertThat(response.getClientSecret()).isEqualTo("secret_1");
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        assertThat(response.getProvider()).isEqualTo("stripe");
        assertThat(response.getPublishableKey()).isEqualTo("pk_test_123");
        // amount passed through as BigDecimal
        verify(paymentService).getOrCreateOrderPaymentIntent(ORDER_ID, USER_ID, List.of("b1", "b2"),
                new BigDecimal("50.00"), "USD");
    }

    @Test
    void createOrderPaymentIntent_mockMode_omitsPublishableKey() {
        ReflectionTestUtils.setField(service, "gatewayType", "mock");
        when(paymentService.getOrCreateOrderPaymentIntent(any(), any(), anyList(), any(), any()))
                .thenReturn(new PaymentIntentResult("pay-1", ORDER_ID, "pi_1", "secret_1", "PROCESSING"));

        withUser(() -> service.createOrderPaymentIntent(orderRequest(), responseObserver));

        ArgumentCaptor<PaymentIntentResponse> captor = ArgumentCaptor.forClass(PaymentIntentResponse.class);
        verify(responseObserver).onNext(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("mock");
        assertThat(captor.getValue().getPublishableKey()).isEmpty();
    }

    @Test
    void createOrderPaymentIntent_noAuthenticatedUser_throws() {
        assertThatThrownBy(() -> service.createOrderPaymentIntent(orderRequest(), responseObserver))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(paymentService);
    }

    @Test
    void confirmMockPayment_available_delegatesAndReturnsStatus() {
        UUID id = UUID.randomUUID();
        PaymentEntity resolved = PaymentEntity.builder()
                .id(id).bookingId(ORDER_ID).userId(USER_ID)
                .amount(BigDecimal.TEN).currency("USD").status(PaymentStatus.COMPLETED).build();
        when(mockConfirmationProvider.getIfAvailable()).thenReturn(mockConfirmationService);
        when(mockConfirmationService.confirm(ORDER_ID, "4242")).thenReturn(resolved);

        ConfirmMockPaymentRequest request = ConfirmMockPaymentRequest.newBuilder()
                .setBookingId(ORDER_ID).setCardNumber("4242").build();
        withUser(() -> service.confirmMockPayment(request, responseObserver));

        ArgumentCaptor<PaymentIntentResponse> captor = ArgumentCaptor.forClass(PaymentIntentResponse.class);
        verify(responseObserver).onNext(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void confirmMockPayment_unavailableInStripeMode_throws() {
        when(mockConfirmationProvider.getIfAvailable()).thenReturn(null);

        ConfirmMockPaymentRequest request = ConfirmMockPaymentRequest.newBuilder()
                .setBookingId(ORDER_ID).setCardNumber("4242").build();

        assertThatThrownBy(() -> withUser(() -> service.confirmMockPayment(request, responseObserver)))
                .isInstanceOf(IllegalStateException.class);
    }
}
