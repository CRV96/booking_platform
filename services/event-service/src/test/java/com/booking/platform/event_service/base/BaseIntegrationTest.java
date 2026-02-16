package com.booking.platform.event_service.base;

import com.booking.platform.event_service.document.EventCategory;
import com.booking.platform.event_service.document.EventStatus;
import com.booking.platform.event_service.document.OrganizerInfo;
import com.booking.platform.event_service.document.SeatCategory;
import com.booking.platform.event_service.document.VenueInfo;
import com.booking.platform.event_service.dto.OrganizerDto;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.document.EventDocument;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Base class for all event-service integration tests.
 *
 * <p>Containers are declared as true JVM-level singletons in this class and started
 * once per test run. All subclasses share the same containers and — because
 * {@code @DynamicPropertySource} always produces the same host/port values — Spring
 * reuses a single application context for the entire suite, which is both fast and
 * correct.
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    // =========================================================================
    // Shared containers — true static singletons on the base class itself.
    // Testcontainers starts them once; all subclasses see the same instances.
    // =========================================================================

    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7"));

    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static {
        MONGO.start();
        REDIS.start();
    }

    // =========================================================================
    // Dynamic properties — called once; always returns the same host/port so
    // Spring's context cache hits on every subsequent test class.
    // =========================================================================

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    // =========================================================================
    // Injected beans
    // =========================================================================

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected CacheManager cacheManager;

    // =========================================================================
    // Setup — clean state before every test
    // =========================================================================

    @BeforeEach
    void cleanState() {
        eventRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
    }

    // =========================================================================
    // Test data builders — reusable across all test classes
    // =========================================================================

    protected OrganizerDto defaultOrganizer() {
        return new OrganizerDto("user-001", "Alice Smith", "alice@example.com");
    }

    protected EventDocument buildAndSaveEvent(String title, EventCategory category,
                                               EventStatus status, Instant dateTime) {
        EventDocument event = EventDocument.builder()
                .title(title)
                .description("Test description for " + title)
                .category(category)
                .status(status)
                .dateTime(dateTime)
                .timezone("UTC")
                .venue(VenueInfo.builder()
                        .name("Test Venue")
                        .city("Bucharest")
                        .country("Romania")
                        .build())
                .organizer(OrganizerInfo.builder()
                        .userId("user-001")
                        .name("Alice Smith")
                        .email("alice@example.com")
                        .build())
                .seatCategories(List.of(
                        SeatCategory.builder()
                                .name("General")
                                .price(50.0)
                                .currency("USD")
                                .totalSeats(100)
                                .availableSeats(100)
                                .build()
                ))
                .build();
        return eventRepository.save(event);
    }

    protected Instant futureDate(int daysFromNow) {
        return Instant.now().plus(daysFromNow, ChronoUnit.DAYS);
    }

    protected Instant pastDate(int daysAgo) {
        return Instant.now().minus(daysAgo, ChronoUnit.DAYS);
    }
}
