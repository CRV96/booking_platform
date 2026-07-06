package com.booking.platform.user_service.entity;

import com.booking.platform.user_service.constants.EntityColumns;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Read-only JPA entity mapping to user_attribute table.
 * DO NOT use this for writes - use Keycloak Admin API instead.
 */
@Entity
@Table(name = EntityColumns.UserAttribute.TABLE)
@Immutable
@Getter
@NoArgsConstructor
public class UserAttributeEntity {

    @Id
    @Column(name = EntityColumns.UserAttribute.ID, length = 36)
    private String id;

    @Column(name = EntityColumns.UserAttribute.NAME, length = 255)
    private String name;

    @Column(name = EntityColumns.UserAttribute.VALUE, length = 255)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = EntityColumns.UserAttribute.USER_ID, referencedColumnName = EntityColumns.User.ID)
    private UserEntity user;
}
