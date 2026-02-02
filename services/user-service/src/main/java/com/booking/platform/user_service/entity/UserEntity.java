package com.booking.platform.user_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only JPA entity mapping to user_entity table.
 * DO NOT use this for writes - use Keycloak Admin API instead.
 */
@Entity
@Table(name = "user_entity")
@Immutable
@Getter
@NoArgsConstructor
public class UserEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    @Column(name = "realm_id", length = 255)
    private String realmId;

    @Column(name = "created_timestamp")
    private Long createdTimestamp;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<UserAttributeEntity> attributes = new ArrayList<>();
}
