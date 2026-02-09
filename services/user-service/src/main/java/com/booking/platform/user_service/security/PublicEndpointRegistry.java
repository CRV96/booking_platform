package com.booking.platform.user_service.security;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry that tracks which gRPC methods are marked as public endpoints.
 *
 * Scans all {@link GrpcService} beans at startup and collects methods
 * annotated with {@link PublicEndpoint}.
 *
 * The interceptor uses this registry to determine if authentication
 * should be enforced for a given method.
 */
@Slf4j
@Component
public class PublicEndpointRegistry {

    private final ApplicationContext applicationContext;
    private final Set<String> publicMethods = new HashSet<>();

    @Autowired
    public PublicEndpointRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        Map<String, Object> grpcServices = applicationContext.getBeansWithAnnotation(GrpcService.class);

        for (Object service : grpcServices.values()) {
            Class<?> serviceClass = service.getClass();

            for (Method method : serviceClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PublicEndpoint.class)) {
                    // gRPC method names are capitalized (e.g., "Login", "Register")
                    String methodName = capitalize(method.getName());
                    publicMethods.add(methodName);
                    log.info("Registered public endpoint: {}", methodName);
                }
            }
        }

        log.info("Public endpoint registry initialized with {} public methods: {}",
                publicMethods.size(), publicMethods);
    }

    /**
     * Checks if a gRPC method is a public endpoint.
     *
     * @param methodName the bare method name from the gRPC call (e.g., "Login")
     * @return true if the method is public, false if authentication is required
     */
    public boolean isPublicEndpoint(String methodName) {
        return publicMethods.contains(methodName);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
