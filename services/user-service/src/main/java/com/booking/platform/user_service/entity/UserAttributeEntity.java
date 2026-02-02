package com.booking.platform.user_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Read-only JPA entity mapping to user_attribute table.
 * DO NOT use this for writes - use Keycloak Admin API instead.
 */
@Entity
@Table(name = "user_attribute")
@Immutable
@Getter
@NoArgsConstructor
public class UserAttributeEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "value", length = 255)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private UserEntity user;
}
