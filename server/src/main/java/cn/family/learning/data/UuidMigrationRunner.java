package cn.family.learning.data;

import javax.sql.DataSource;
import java.sql.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.role",havingValue="migrate")
public class UuidMigrationRunner implements ApplicationRunner {
    private final DataSource target;private final Environment environment;
    public UuidMigrationRunner(DataSource target,Environment environment){this.target=target;this.environment=environment;}
    @Override public void run(ApplicationArguments arguments)throws Exception {
        String url=required("MIGRATION_SOURCE_DB_URL"),user=required("MIGRATION_SOURCE_DB_USER"),password=required("MIGRATION_SOURCE_DB_PASSWORD");
        try(Connection source=DriverManager.getConnection(url,user,password);Connection destination=target.getConnection()) {
            try(Statement statement=source.createStatement()){statement.execute("SET time_zone = '+00:00'");}
            UuidDataMigrator.Report result=new UuidDataMigrator().copy(source,destination);
            LoggerFactory.getLogger(getClass()).info("UUID migration completed. Row counts: {}; expired media references: {}. Source unchanged; sessions revoked; pending jobs cancelled.",result.counts,result.expiredMediaReferences);
        }
    }
    private String required(String key){String value=environment.getProperty(key);if(value==null||value.trim().isEmpty())throw new IllegalArgumentException("Set "+key+" in the local environment");return value;}
}
