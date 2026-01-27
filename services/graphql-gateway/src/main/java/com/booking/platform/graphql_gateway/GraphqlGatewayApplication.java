package com.booking.platform.graphql_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GraphqlGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GraphqlGatewayApplication.class, args);
	}

}
