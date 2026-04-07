package com.booking.platform.graphql_gateway.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private Tier anonymous = new Tier(30, 60);
    private Tier authenticated = new Tier(100, 60);

    @Getter
    @Setter
    public static class Tier {
        private int limit;
        private int windowSeconds;

        public Tier() {}

        public Tier(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
    }
}
