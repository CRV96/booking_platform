package com.booking.platform.booking_service.config;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * Configures mTLS for outgoing gRPC client calls (booking-service → event-service).
 *
 * When enabled, the client:
 * - Presents its certificate to servers
 * - Verifies server certificates against the trusted CA
 *
 * Enable with: grpc.client.security.enabled=true
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "grpc.client.security.enabled", havingValue = "true")
public class GrpcClientTlsConfig {

    @Value("${grpc.client.security.certificate-chain}")
    private Resource certificateChain;

    @Value("${grpc.client.security.private-key}")
    private Resource privateKey;

    @Value("${grpc.client.security.trust-certificate}")
    private Resource trustCertificate;

    @Bean
    public GrpcChannelConfigurer grpcChannelConfigurer() {
        return (channelBuilder, name) -> {
            if (channelBuilder instanceof NettyChannelBuilder nettyBuilder) {
                try {
                    log.info("Configuring gRPC client '{}' with mTLS...", name);

                    SslContext sslContext = GrpcSslContexts.configure(
                            SslContextBuilder.forClient()
                                    .keyManager(
                                            certificateChain.getInputStream(),
                                            privateKey.getInputStream()
                                    )
                                    .trustManager(trustCertificate.getInputStream())
                    ).build();

                    nettyBuilder.sslContext(sslContext);
                    log.info("gRPC client '{}' mTLS configured successfully", name);

                } catch (IOException e) {
                    log.error("Failed to configure gRPC client TLS for '{}'", name, e);
                    throw new RuntimeException("Failed to configure gRPC client TLS", e);
                }
            } else {
                log.warn("Cannot configure TLS for non-Netty channel builder: {}",
                        channelBuilder.getClass().getName());
            }
        };
    }
}
