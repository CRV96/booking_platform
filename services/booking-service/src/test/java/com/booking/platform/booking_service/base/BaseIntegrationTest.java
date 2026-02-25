package com.booking.platform.booking_service.base;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for all booking-service integration tests.
 *
 * <p>Starts real PostgreSQL, Redis, and Kafka containers via Testcontainers.
 * All containers are static singletons so they start once per JVM and are shared
 * across subclasses — Spring's context cache reuses a single application context
 * for the entire test suite.
 *
 * <p>Flyway runs automatically on context startup and applies V1__create_bookings_table.sql
 * against the Testcontainer PostgreSQL instance.
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    // =========================================================================
    // Shared containers — true static singletons, started once per JVM.
    // =========================================================================

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

    // =========================================================================
    // Dynamic properties — same values every time → Spring context cache hits.
    // =========================================================================

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "booking-service-test-group");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        // Disable Eureka registration in tests
        registry.add("eureka.client.enabled", () -> "false");
        // Disable gRPC server startup in tests (no port conflict)
        registry.add("grpc.server.port", () -> "-1");
        // Disable mTLS in tests (no certs available)
        registry.add("grpc.server.security.enabled", () -> "false");
        // Disable JWT validation in tests
        registry.add("security.jwt.enabled", () -> "false");
        // Kafka consumer settings (required by KafkaConsumerConfig)
        registry.add("spring.kafka.consumer.group-id", () -> "booking-service-test-group");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    // =========================================================================
    // Injected beans
    // =========================================================================

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    // =========================================================================
    // Setup — clean bookings table before each test
    // =========================================================================

    @BeforeEach
    void cleanState() {
        jdbcTemplate.execute("DELETE FROM bookings");
    }
}
