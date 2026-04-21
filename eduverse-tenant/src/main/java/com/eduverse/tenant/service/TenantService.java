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

    public java.util.Optional<Tenant> findTenantById(String tenantId) {
        return tenantRepository.findByTenantId(tenantId);
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
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'%1$s.users') AND type in (N'U'))
            BEGIN
                CREATE TABLE %1$s.users (
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
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'%1$s.refresh_tokens') AND type in (N'U'))
            BEGIN
                CREATE TABLE %1$s.refresh_tokens (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    token NVARCHAR(255) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    expiry_date DATETIME2 NOT NULL,
                    revoked BIT NOT NULL DEFAULT 0,
                    CONSTRAINT FK_RefreshTokens_User FOREIGN KEY (user_id) REFERENCES %1$s.users(id)
                )
            END
            -- Courses Table
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'%1$s.courses') AND type in (N'U'))
            BEGIN
                CREATE TABLE %1$s.courses (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    title NVARCHAR(255) NOT NULL,
                    description NVARCHAR(MAX),
                    thumbnail_url NVARCHAR(MAX),
                    published BIT NOT NULL DEFAULT 0,
                    tenant_id NVARCHAR(55) NOT NULL,
                    created_at DATETIME2 DEFAULT GETDATE(),
                    updated_at DATETIME2 DEFAULT GETDATE()
                )
            END

            -- Lessons Table
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'%1$s.lessons') AND type in (N'U'))
            BEGIN
                CREATE TABLE %1$s.lessons (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    title NVARCHAR(255) NOT NULL,
                    description NVARCHAR(MAX),
                    youtube_video_id NVARCHAR(50),
                    youtube_url NVARCHAR(1000),
                    duration_seconds BIGINT,
                    order_index INT,
                    video_path NVARCHAR(500),
                    video_status NVARCHAR(50),
                    video_type NVARCHAR(50),
                    original_file_name NVARCHAR(500),
                    course_id BIGINT NOT NULL,
                    tenant_id NVARCHAR(55) NOT NULL,
                    created_at DATETIME2 DEFAULT GETDATE(),
                    updated_at DATETIME2 DEFAULT GETDATE(),
                    CONSTRAINT FK_Lessons_Course FOREIGN KEY (course_id) REFERENCES %1$s.courses(id) ON DELETE CASCADE
                )
            END
            """.formatted(schemaName);

        jdbcTemplate.execute(createTablesSql);

        // Migrate: add new columns to existing lessons table if they don't exist
        addColumnIfNotExists(schemaName, "lessons", "video_path", "NVARCHAR(500)");
        addColumnIfNotExists(schemaName, "lessons", "video_status", "NVARCHAR(50)");
        addColumnIfNotExists(schemaName, "lessons", "video_type", "NVARCHAR(50)");
        addColumnIfNotExists(schemaName, "lessons", "original_file_name", "NVARCHAR(500)");
    }

    private void addColumnIfNotExists(String schema, String table, String column, String type) {
        String sql = "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '" + schema +
                     "' AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "') " +
                     "BEGIN ALTER TABLE [" + schema + "].[" + table + "] ADD [" + column + "] " + type + " NULL END";
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("Could not add column {}.{}.{}: {}", schema, table, column, e.getMessage());
        }
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
