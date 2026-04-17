package com.booking.platform.common.security;

import net.devh.boot.grpc.server.service.GrpcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicEndpointRegistryTest {

    @Mock
    private ApplicationContext applicationContext;

    private PublicEndpointRegistry registry;

    @GrpcService
    static class SampleGrpcService {
        @PublicEndpoint
        public void getEvent() {}

        @PublicEndpoint
        public void searchEvents() {}

        public void createEvent() {}
    }

    @GrpcService
    static class AnotherGrpcService {
        @PublicEndpoint
        public void login() {}
    }

    @BeforeEach
    void setUp() {
        registry = new PublicEndpointRegistry(applicationContext);
    }

    @Test
    void init_registersMethodsAnnotatedWithPublicEndpoint() {
        when(applicationContext.getBeansWithAnnotation(GrpcService.class))
                .thenReturn(Map.of("sampleService", new SampleGrpcService()));

        registry.init();

        assertThat(registry.isPublicEndpoint("GetEvent")).isTrue();
        assertThat(registry.isPublicEndpoint("SearchEvents")).isTrue();
    }

    @Test
    void init_doesNotRegisterMethodsWithoutAnnotation() {
        when(applicationContext.getBeansWithAnnotation(GrpcService.class))
                .thenReturn(Map.of("sampleService", new SampleGrpcService()));

        registry.init();

        assertThat(registry.isPublicEndpoint("CreateEvent")).isFalse();
    }

    @Test
    void init_registersPublicMethodsAcrossMultipleServices() {
        when(applicationContext.getBeansWithAnnotation(GrpcService.class))
                .thenReturn(Map.of(
                        "sampleService", new SampleGrpcService(),
                        "anotherService", new AnotherGrpcService()));

        registry.init();

        assertThat(registry.isPublicEndpoint("GetEvent")).isTrue();
        assertThat(registry.isPublicEndpoint("Login")).isTrue();
    }

    @Test
    void init_withNoGrpcServices_registersNothing() {
        when(applicationContext.getBeansWithAnnotation(GrpcService.class))
                .thenReturn(Map.of());

        registry.init();

        assertThat(registry.isPublicEndpoint("Anything")).isFalse();
    }

    @Test
    void isPublicEndpoint_isCaseSensitive() {
        when(applicationContext.getBeansWithAnnotation(GrpcService.class))
                .thenReturn(Map.of("sampleService", new SampleGrpcService()));
        registry.init();

        assertThat(registry.isPublicEndpoint("GetEvent")).isTrue();
        assertThat(registry.isPublicEndpoint("getEvent")).isFalse();
        assertThat(registry.isPublicEndpoint("GETEVENT")).isFalse();
    }
}
