package cn.family.learning.data.types;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.TimeZone;
import org.apache.ibatis.type.*;

/** Connector/J 5.1 implements JDBC 4.0, so avoid JDBC 4.2 getObject(LocalDateTime.class). */
@MappedTypes(LocalDateTime.class)
@MappedJdbcTypes(value=JdbcType.TIMESTAMP, includeNullJdbcType=true)
public class UtcLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {
    private static Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
    private static LocalDateTime value(Timestamp timestamp) {
        return timestamp == null ? null : LocalDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
    }
    @Override public void setNonNullParameter(PreparedStatement ps,int index,LocalDateTime value,JdbcType type)throws SQLException {
        ps.setTimestamp(index,Timestamp.from(value.toInstant(ZoneOffset.UTC)),utc());
    }
    @Override public LocalDateTime getNullableResult(ResultSet rs,String name)throws SQLException { return value(rs.getTimestamp(name,utc())); }
    @Override public LocalDateTime getNullableResult(ResultSet rs,int index)throws SQLException { return value(rs.getTimestamp(index,utc())); }
    @Override public LocalDateTime getNullableResult(CallableStatement cs,int index)throws SQLException { return value(cs.getTimestamp(index,utc())); }
}
