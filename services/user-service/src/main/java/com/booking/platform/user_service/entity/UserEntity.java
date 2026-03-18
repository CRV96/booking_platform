package com.booking.platform.user_service.entity;

import com.booking.platform.user_service.constants.EntityColumns;
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
@Table(name = EntityColumns.User.TABLE)
@Immutable
@Getter
@NoArgsConstructor
public class UserEntity {

    @Id
    @Column(name = EntityColumns.User.ID, length = 36)
    private String id;

    @Column(name = EntityColumns.User.USERNAME, length = 255)
    private String username;

    @Column(name = EntityColumns.User.EMAIL, length = 255)
    private String email;

    @Column(name = EntityColumns.User.EMAIL_VERIFIED)
    private Boolean emailVerified;

    @Column(name = EntityColumns.User.ENABLED)
    private Boolean enabled;

    @Column(name = EntityColumns.User.FIRST_NAME, length = 255)
    private String firstName;

    @Column(name = EntityColumns.User.LAST_NAME, length = 255)
    private String lastName;

    @Column(name = EntityColumns.User.REALM_ID, length = 255)
    private String realmId;

    @Column(name = EntityColumns.User.CREATED_TIMESTAMP)
    private Long createdTimestamp;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<UserAttributeEntity> attributes = new ArrayList<>();
}
