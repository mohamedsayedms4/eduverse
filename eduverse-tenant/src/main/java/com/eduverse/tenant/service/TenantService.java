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

    @Transactional
    public Tenant registerTenant(String tenantId, String name) {
        log.info("Registering new tenant: {} ({})", name, tenantId);
        
        String schemaName = tenantId; // Using tenantId directly as schema name per user expectation
        
        // 1. Create Schema in SQL Server
        createSchema(schemaName);
        
        // 2. Create foundational tables in the new schema
        createTenantTables(schemaName);
        
        // 3. Create DNS record in Cloudflare
        cloudflareService.createSubdomain(tenantId);
        
        // 4. Save to Registry
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(name);
        tenant.setDbSchema(schemaName);
        
        return tenantRepository.save(tenant);
    }

    private void createSchema(String schemaName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            log.info("Creating schema: {}", schemaName);
            statement.execute("IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = '" + schemaName + "') " +
                              "BEGIN EXEC('CREATE SCHEMA " + schemaName + "') END");
            
        } catch (SQLException e) {
            log.error("Failed to create schema: {}", schemaName, e);
            throw new RuntimeException("Schema creation failed", e);
        }
    }

    private void createTenantTables(String schemaName) {
        String createTeachersTable = """
            IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'%s.teachers') AND type in (N'U'))
            BEGIN
                CREATE TABLE %s.teachers (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    first_name NVARCHAR(50) NOT NULL,
                    last_name NVARCHAR(50) NOT NULL,
                    email NVARCHAR(100) NOT NULL UNIQUE,
                    password NVARCHAR(255) NOT NULL,
                    active BIT NOT NULL DEFAULT 1,
                    created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
                    updated_at DATETIME2 NOT NULL DEFAULT GETDATE()
                )
            END
            """.formatted(schemaName, schemaName);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            log.info("Creating tables for schema: {}", schemaName);
            statement.execute(createTeachersTable);
            
        } catch (SQLException e) {
            log.error("Failed to create tables for schema: {}", schemaName, e);
            throw new RuntimeException("Table creation failed", e);
        }
    }
}
