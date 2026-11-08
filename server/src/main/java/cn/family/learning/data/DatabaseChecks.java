package cn.family.learning.data;

import java.sql.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** Legacy InnoDB READ COMMITTED transactions require row-based binary logging when enabled. */
@Component
public class DatabaseChecks implements InitializingBean {
    private final DataSource dataSource;
    public DatabaseChecks(DataSource dataSource) { this.dataSource=dataSource; }
    @Override public void afterPropertiesSet()throws SQLException {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement();
                ResultSet result=statement.executeQuery("SELECT @@log_bin, @@binlog_format")) {
            if(result.next() && result.getBoolean(1) && !"ROW".equalsIgnoreCase(result.getString(2)))
                throw new IllegalStateException("This application requires binlog_format=ROW when MySQL binary logging is enabled; it does not change server settings automatically.");
        }
    }
}
