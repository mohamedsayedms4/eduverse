package com.eduverse.tenant.service;

import com.eduverse.tenant.model.Tenant;
import com.eduverse.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs on startup to migrate all existing tenant schemas with new columns.
 * Safe to run multiple times — uses IF NOT EXISTS checks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantMigrationRunner implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("Running tenant schema migration...");
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            String schema = tenant.getDbSchema();
            try {
                addColumnIfNotExists(schema, "lessons", "video_path", "NVARCHAR(500)");
                addColumnIfNotExists(schema, "lessons", "video_status", "NVARCHAR(50)");
                addColumnIfNotExists(schema, "lessons", "video_type", "NVARCHAR(50)");
                addColumnIfNotExists(schema, "lessons", "original_file_name", "NVARCHAR(500)");
                log.info("Migrated tenant schema: {}", schema);
            } catch (Exception e) {
                log.error("Failed to migrate tenant schema: {}", schema, e);
            }
        }

        // Also migrate dbo schema
        try {
            addColumnIfNotExists("dbo", "lessons", "video_path", "NVARCHAR(500)");
            addColumnIfNotExists("dbo", "lessons", "video_status", "NVARCHAR(50)");
            addColumnIfNotExists("dbo", "lessons", "video_type", "NVARCHAR(50)");
            addColumnIfNotExists("dbo", "lessons", "original_file_name", "NVARCHAR(500)");
            log.info("Migrated dbo schema");
        } catch (Exception e) {
            log.warn("dbo.lessons migration skipped (table may not exist): {}", e.getMessage());
        }

        // Fix stuck PROCESSING lessons (from previous async thread bug)
        for (Tenant tenant : tenants) {
            String schema = tenant.getDbSchema();
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE [" + schema + "].lessons SET video_status = 'READY' WHERE video_status = 'PROCESSING' AND video_type = 'SELF_HOSTED'"
                );
                if (updated > 0) {
                    log.info("Fixed {} stuck PROCESSING lessons in schema: {}", updated, schema);
                }
            } catch (Exception e) {
                log.warn("Could not fix stuck lessons in {}: {}", schema, e.getMessage());
            }
        }

        log.info("Tenant schema migration complete.");
    }

    private void addColumnIfNotExists(String schema, String table, String column, String type) {
        String sql = "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '" + schema +
                     "' AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "') " +
                     "BEGIN ALTER TABLE [" + schema + "].[" + table + "] ADD [" + column + "] " + type + " NULL END";
        jdbcTemplate.execute(sql);
    }
}
