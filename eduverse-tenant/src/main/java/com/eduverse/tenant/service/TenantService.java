package com.eduverse.tenant.service;

import com.eduverse.tenant.model.Tenant;
import com.eduverse.tenant.repository.TenantRepository;
import com.eduverse.tenant.service.dns.CloudflareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final DataSource dataSource;
    private final CloudflareService cloudflareService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Transactional
    public Tenant registerTenant(String tenantId, String name, String firstName, String lastName, String email, String password) {
        log.info("Registering new tenant: {} ({})", name, tenantId);
        
        String schemaName = tenantId; 
        
        // 1. Create Schema and Tables
        createSchema(schemaName);
        createTenantTables(schemaName);
        
        // 2. Insert Initial Teacher User
        createInitialUser(schemaName, firstName, lastName, email, password);
        
        // 3. Create DNS record
        cloudflareService.createSubdomain(tenantId);
        
        // 4. Save to Registry
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(name);
        tenant.setDbSchema(schemaName);
        tenant.setAdminEmail(email);
        
        return tenantRepository.save(tenant);
    }

    public java.util.Optional<Tenant> findTenantByAdminEmail(String email) {
        return tenantRepository.findByAdminEmail(email);
    }

    private void createSchema(String schemaName) {
        log.info("Creating schema: {}", schemaName);
        jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = '" + schemaName + "') " +
                             "BEGIN EXEC('CREATE SCHEMA " + schemaName + "') END");
    }

    private void createTenantTables(String schemaName) {
        log.info("Creating tables for schema: {}", schemaName);
        String createTablesSql = """
            -- Users Table
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'%s.users') AND type in (N'U'))
            BEGIN
                CREATE TABLE %s.users (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    first_name NVARCHAR(100) NOT NULL,
                    last_name NVARCHAR(100) NOT NULL,
                    email NVARCHAR(255) NOT NULL UNIQUE,
                    password NVARCHAR(255) NOT NULL,
                    role NVARCHAR(50) NOT NULL,
                    tenant_id NVARCHAR(55) NULL,
                    active BIT NOT NULL DEFAULT 1,
                    created_at DATETIME2 DEFAULT GETDATE(),
                    updated_at DATETIME2 DEFAULT GETDATE()
                )
            END

            -- Refresh Tokens Table
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'%s.refresh_tokens') AND type in (N'U'))
            BEGIN
                CREATE TABLE %s.refresh_tokens (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    token NVARCHAR(255) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    expiry_date DATETIME2 NOT NULL,
                    revoked BIT NOT NULL DEFAULT 0,
                    CONSTRAINT FK_RefreshTokens_User FOREIGN KEY (user_id) REFERENCES %s.users(id)
                )
            END
            """.formatted(schemaName, schemaName, schemaName, schemaName, schemaName);

        jdbcTemplate.execute(createTablesSql);
    }

    private void createInitialUser(String schemaName, String firstName, String lastName, String email, String password) {
        String encodedPassword = passwordEncoder.encode(password);
        String insertSql = """
            INSERT INTO %s.users (first_name, last_name, email, password, role, tenant_id)
            VALUES (?, ?, ?, ?, 'TEACHER', ?)
            """.formatted(schemaName);

        jdbcTemplate.update(insertSql, firstName, lastName, email, encodedPassword, schemaName);
        log.info("Initial teacher user created for tenant: {}", schemaName);
    }
}
