package com.booking.platform.booking_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(
		scanBasePackages = {
				"com.booking.platform.booking_service",
				"com.booking.platform.common"
		}
)
@EnableDiscoveryClient
public class BookingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingServiceApplication.class, args);
	}

}
