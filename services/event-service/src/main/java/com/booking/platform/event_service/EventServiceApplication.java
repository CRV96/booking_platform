package com.booking.platform.event_service;

import com.booking.platform.event_service.properties.EventProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(
		scanBasePackages = {
				"com.booking.platform.event_service",
				"com.booking.platform.common"
		}
)
@EnableDiscoveryClient
@EnableConfigurationProperties(EventProperties.class)
public class EventServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventServiceApplication.class, args);
	}

}
