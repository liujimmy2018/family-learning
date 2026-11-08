package cn.family.learning.data;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The legacy Flyway API is configured explicitly instead of Boot's newer Flyway integration. */
@Configuration
@ConditionalOnProperty(name="app.db-migrations-enabled", havingValue="true", matchIfMissing=true)
public class DatabaseMigrations {
    @Bean
    public Flyway legacyFlyway(DataSource dataSource,DatabaseChecks checks) {
        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setLocations("classpath:db/mysql51-uuid");
        flyway.setTable("family_uuid_schema_history");
        flyway.setEncoding("UTF-8");
        flyway.setBaselineOnMigrate(false);
        flyway.setCleanDisabled(true);
        flyway.setValidateOnMigrate(true);
        flyway.migrate();
        return flyway;
    }
}
