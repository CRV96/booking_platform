package com.booking.platform.common.scheduling;

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Auto-configuration for distributed scheduling via ShedLock.
 *
 * <p>Activates automatically for any service that:
 * <ul>
 *   <li>Has {@code shedlock-provider-jdbc-template} on the classpath</li>
 *   <li>Has a {@link DataSource} bean (i.e. uses PostgreSQL)</li>
 * </ul>
 *
 * <p>By declaring {@code @EnableScheduling} and {@code @EnableSchedulerLock}
 * here, services no longer need to enable scheduling or scheduler-lock
 * themselves — adding the ShedLock dependency is enough.
 *
 * <p>Per-method {@code @SchedulerLock(lockAtMostFor = "...")} values always
 * override the {@code defaultLockAtMostFor} set here.
 *
 * <p>Registered via {@code META-INF/spring/...AutoConfiguration.imports}
 * so services do not need to import or component-scan this class explicitly.
 */
@AutoConfiguration
@ConditionalOnClass(JdbcTemplateLockProvider.class)
@ConditionalOnBean(DataSource.class)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class ShedLockConfig {

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplateLockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
