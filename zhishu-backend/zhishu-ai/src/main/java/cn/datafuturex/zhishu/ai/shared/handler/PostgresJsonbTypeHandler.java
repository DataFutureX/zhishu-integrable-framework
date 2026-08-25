package cn.datafuturex.zhishu.ai.shared.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL {@code jsonb} ↔ Java {@link String}。
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PostgresJsonbTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(parameter);
        ps.setObject(i, jsonb);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toString(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toString(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toString(cs.getObject(columnIndex));
    }

    private static String toString(Object value) {
        return value == null ? null : value.toString();
    }
}
