package com.yowpainter.shared.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantMigrationService {

    private final DataSource dataSource;
    private final org.springframework.core.env.Environment environment;

    public void migrateTenant(UUID organizationId) {
        if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            log.info("Skipping tenant migration in test profile");
            return;
        }
        String schemaName = "tenant_" + organizationId.toString().replace("-", "_");
        createSchemaIfNotExist(schemaName);

        log.info("Running Liquibase migration for tenant schema: {}", schemaName);
        try (Connection connection = dataSource.getConnection()) {
            liquibase.database.Database database = liquibase.database.DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(connection));
            database.setDefaultSchemaName(schemaName);
            database.setLiquibaseSchemaName(schemaName);

            try (liquibase.Liquibase liquibaseInstance = new liquibase.Liquibase(
                    "db/changelog/db.changelog-tenant.yaml",
                    new liquibase.resource.ClassLoaderResourceAccessor(),
                    database)) {
                liquibaseInstance.update("");
            }
            log.info("Liquibase migration completed for tenant schema: {}", schemaName);
        } catch (Exception e) {
            log.error("Failed to run Liquibase migrations for tenant schema: {}", schemaName, e);
            throw new RuntimeException("Could not migrate schema " + schemaName, e);
        }
    }

    private void createSchemaIfNotExist(String schemaName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            log.info("Schema {} created or already exists", schemaName);
        } catch (SQLException e) {
            log.error("Failed to create schema {}", schemaName, e);
            throw new RuntimeException("Could not create schema " + schemaName, e);
        }
    }
}
