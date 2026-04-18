package com.eduverse.tenant.model;

import com.eduverse.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenants", schema = "dbo")
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String dbSchema;

    @Column(nullable = false, unique = true)
    private String adminEmail;

    @Column(nullable = false)
    private boolean active = true;
}
