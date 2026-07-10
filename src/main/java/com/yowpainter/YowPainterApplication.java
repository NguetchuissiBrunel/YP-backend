package com.yowpainter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class YowPainterApplication {

    public static void main(String[] args) {
        loadEnvFile(".env");
        loadEnvFile(".env.local");
        runLiquibaseMigrations();
        SpringApplication.run(YowPainterApplication.class, args);
    }

    private static void runLiquibaseMigrations() {
        String url = System.getProperty("SPRING_DATASOURCE_URL");
        String user = System.getProperty("SPRING_DATASOURCE_USERNAME");
        String pass = System.getProperty("SPRING_DATASOURCE_PASSWORD");
        if (url == null || url.isEmpty()) {
            return;
        }
        System.out.println("Running programmatic Liquibase migrations on public schema...");
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(url, user, pass)) {
            liquibase.database.Database database = liquibase.database.DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(connection));
            database.setDefaultSchemaName("public");
            database.setLiquibaseSchemaName("public");

            try (liquibase.Liquibase liquibaseInstance = new liquibase.Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new liquibase.resource.ClassLoaderResourceAccessor(),
                    database)) {
                liquibaseInstance.update("");
            }
            System.out.println("Programmatic Liquibase migrations completed successfully.");
        } catch (Exception e) {
            System.err.println("Failed to run Liquibase migrations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadEnvFile(String filename) {
        java.io.File file = new java.io.File(filename);
        if (!file.exists()) {
            return;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    System.setProperty(key, value);
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("Failed to load environment file " + filename + ": " + e.getMessage());
        }
    }

}
