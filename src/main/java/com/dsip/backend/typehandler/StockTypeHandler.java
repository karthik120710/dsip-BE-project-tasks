package com.dsip.backend.typehandler;

import com.dsip.backend.enums.StockType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler for converting between StockType enum and database SMALLINT.
 */
@MappedTypes(StockType.class)
@MappedJdbcTypes(JdbcType.SMALLINT)
public class StockTypeHandler extends BaseTypeHandler<StockType> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, StockType parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getValue());
    }

    @Override
    public StockType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : StockType.fromValue(value);
    }

    @Override
    public StockType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : StockType.fromValue(value);
    }

    @Override
    public StockType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : StockType.fromValue(value);
    }
}
