package com.vtesdecks.db.handlers;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

@MappedTypes(LocalDate.class)
public class LocalDateHandler extends BaseTypeHandler<LocalDate> {

    @Override
    public LocalDate getNullableResult(ResultSet rs, String name) throws SQLException {
        Date date = rs.getDate(name);
        return date != null ? date.toLocalDate() : null;
    }

    @Override
    public LocalDate getNullableResult(ResultSet rs, int index) throws SQLException {
        Date date = rs.getDate(index);
        return date != null ? date.toLocalDate() : null;
    }

    @Override
    public LocalDate getNullableResult(CallableStatement cs, int index) throws SQLException {
        Date date = cs.getDate(index);
        return date != null ? date.toLocalDate() : null;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, LocalDate localDate, JdbcType jdbcType) throws SQLException {
        Date date = new Date(localDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
        ps.setDate(index, date);
    }

}
