package com.booking.platform.payment_service;

import com.booking.platform.payment_service.base.BaseIntegrationTest;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Smoke test — verifies that the Spring application context loads successfully
 * with real PostgreSQL, Redis, and Kafka containers.
 *
 * <p>The {@link PaymentGateway} is mocked to avoid gateway delays during context loading.
 */
class PaymentServiceApplicationTests extends BaseIntegrationTest {

    @MockBean
    private PaymentGateway paymentGateway;

    @Test
    void contextLoads() {
        // If this test passes, the application context (including Flyway migrations,
        // JPA entity validation, Kafka topic creation, and scheduled beans) started correctly.
    }
}
