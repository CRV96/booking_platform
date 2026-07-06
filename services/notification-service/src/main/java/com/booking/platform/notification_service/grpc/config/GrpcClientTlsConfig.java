package com.booking.platform.notification_service.grpc.config;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * Configures mTLS for outgoing gRPC client calls (notification-service → user-service).
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
                    ApplicationLogger.logMessage(log, Level.INFO, "Configuring gRPC client '{}' with mTLS...", name);

                    SslContext sslContext = GrpcSslContexts.configure(
                            SslContextBuilder.forClient()
                                    .keyManager(
                                            certificateChain.getInputStream(),
                                            privateKey.getInputStream()
                                    )
                                    .trustManager(trustCertificate.getInputStream())
                    ).build();

                    nettyBuilder.sslContext(sslContext);
                    ApplicationLogger.logMessage(log, Level.INFO, "gRPC client '{}' mTLS configured successfully", name);

                } catch (IOException e) {
                    ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.TLS_CONFIG_FAILED, "gRPC client '{}'", name, e);
                    throw new RuntimeException("Failed to configure gRPC client TLS", e);
                }
            } else {
                ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.TLS_CONFIG_FAILED,
                        "Cannot configure TLS for non-Netty channel builder: {}",
                        channelBuilder.getClass().getName());
            }
        };
    }
}
