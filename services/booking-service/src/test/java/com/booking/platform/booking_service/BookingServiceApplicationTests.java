package com.booking.platform.booking_service;

import com.booking.platform.booking_service.base.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

class BookingServiceApplicationTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
		// Verifies the Spring context starts correctly with Testcontainers (PostgreSQL + Redis + Kafka)
		// and Flyway applies V1__create_bookings_table.sql successfully.
	}

}
