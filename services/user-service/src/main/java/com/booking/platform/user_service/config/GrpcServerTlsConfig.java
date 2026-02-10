package com.booking.platform.user_service.config;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * Configures mTLS for the gRPC server.
 *
 * When enabled, the server:
 * - Presents its certificate to clients
 * - Requires clients to present a valid certificate
 * - Verifies client certificates against the trusted CA
 *
 * Enable with: grpc.server.security.enabled=true
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "grpc.server.security.enabled", havingValue = "true")
public class GrpcServerTlsConfig {

    @Value("${grpc.server.security.certificate-chain}")
    private Resource certificateChain;

    @Value("${grpc.server.security.private-key}")
    private Resource privateKey;

    @Value("${grpc.server.security.trust-certificate}")
    private Resource trustCertificate;

    @Bean
    public GrpcServerConfigurer grpcServerConfigurer() {
        return serverBuilder -> {
            if (serverBuilder instanceof NettyServerBuilder nettyBuilder) {
                try {
                    log.info("Configuring gRPC server with mTLS...");
                    log.info("  Certificate: {}", certificateChain.getFilename());
                    log.info("  Private Key: {}", privateKey.getFilename());
                    log.info("  Trust CA: {}", trustCertificate.getFilename());

                    SslContext sslContext = GrpcSslContexts.configure(
                            SslContextBuilder.forServer(
                                    certificateChain.getInputStream(),
                                    privateKey.getInputStream()
                            )
                    )
                    .trustManager(trustCertificate.getInputStream())
                    .clientAuth(ClientAuth.REQUIRE)  // Require client certificate
                    .build();

                    nettyBuilder.sslContext(sslContext);
                    log.info("gRPC server mTLS configured successfully");

                } catch (IOException e) {
                    log.error("Failed to configure gRPC server TLS", e);
                    throw new RuntimeException("Failed to configure gRPC server TLS", e);
                }
            } else {
                log.warn("Cannot configure TLS for non-Netty server builder: {}", serverBuilder.getClass().getName());
            }
        };
    }
}
