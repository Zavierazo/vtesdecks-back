package com.vtesdecks.db.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(JsonNode.class)
public class JsonNodeHandler extends BaseTypeHandler<JsonNode> {

    private ObjectMapper mapper = new ObjectMapper();


    @Override
    public JsonNode getNullableResult(ResultSet rs, String name) throws SQLException {
        String jsonString = rs.getString(name);
        return getJsonNode(jsonString);
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, int index) throws SQLException {
        String jsonString = rs.getString(index);
        return getJsonNode(jsonString);
    }

    @Override
    public JsonNode getNullableResult(CallableStatement cs, int index) throws SQLException {
        String jsonString = cs.getString(index);
        return getJsonNode(jsonString);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, JsonNode value, JdbcType jdbcType) throws SQLException {
        try {
            String jsonString = mapper.writeValueAsString(value);
            ps.setString(index, jsonString);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode getJsonNode(String jsonString) {
        try {
            return jsonString != null ? mapper.readTree(jsonString) : null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
