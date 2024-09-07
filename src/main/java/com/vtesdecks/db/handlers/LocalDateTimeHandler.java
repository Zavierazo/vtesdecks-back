package com.vtesdecks.db.handlers;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@MappedTypes(LocalDateTime.class)
public class LocalDateTimeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String name) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(name);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int index) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(index);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int index) throws SQLException {
        Timestamp timestamp = cs.getTimestamp(index);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, LocalDateTime localDateTime, JdbcType jdbcType) throws SQLException {
        ps.setTimestamp(index, Timestamp.from(localDateTime.toInstant(OffsetDateTime.now().getOffset())));

    }

}
