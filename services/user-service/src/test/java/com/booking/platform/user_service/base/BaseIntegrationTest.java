package com.booking.platform.user_service.base;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

/**
 * Base class for all user-service integration tests.
 *
 * <p>Starts a real PostgreSQL container and a real Redis container via Testcontainers.
 * Both are declared as static singletons on this base class so they are started once
 * per JVM and shared across all subclasses — Spring's context cache sees the same
 * host/port values every time and reuses a single application context for the entire suite.
 *
 * <p>The {@code UserEntity} and {@code UserAttributeEntity} tables are Keycloak's own schema.
 * Since JPA entities are {@code @Immutable}, writes go through {@link JdbcTemplate} directly —
 * the same pattern Keycloak itself uses to persist users.
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    // =========================================================================
    // Shared containers — true static singletons on the base class.
    // Started once; all subclasses share the same instance.
    // =========================================================================

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static {
        POSTGRES.start();
        REDIS.start();
    }

    // =========================================================================
    // Dynamic properties — always returns the same values so Spring's context
    // cache hits on every subsequent test class.
    // =========================================================================

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    // =========================================================================
    // Injected beans
    // =========================================================================

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected CacheManager cacheManager;

    // =========================================================================
    // Setup — clean state before every test
    // =========================================================================

    @BeforeEach
    void cleanState() {
        // Delete in dependency order: attributes reference users
        jdbcTemplate.execute("DELETE FROM user_attribute");
        jdbcTemplate.execute("DELETE FROM user_entity");

        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
    }

    // =========================================================================
    // Test data builders
    // =========================================================================

    /**
     * Inserts a user directly into the user_entity table, mirroring Keycloak's schema.
     * Returns the generated UUID so tests can reference it.
     */
    protected String insertUser(String username, String email,
                                String firstName, String lastName, String realmId) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO user_entity (id, username, email, email_verified, enabled, " +
                "first_name, last_name, realm_id, created_timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, username, email, true, true,
                firstName, lastName, realmId, System.currentTimeMillis()
        );
        return id;
    }

    /**
     * Inserts a user attribute (e.g. phoneNumber, country) for a given user.
     */
    protected void insertAttribute(String userId, String name, String value) {
        jdbcTemplate.update(
                "INSERT INTO user_attribute (id, name, value, user_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), name, value, userId
        );
    }
}
