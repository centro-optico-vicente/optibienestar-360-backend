package com.fenixcore.optibienestar360.core.jpa;

import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Reports {@link SqlTypes#OTHER} so schema validation matches what the driver
 * reports for citext columns, but binds/extracts as plain VARCHAR. Binding
 * with {@code Types.OTHER} directly makes pgJDBC send the parameter as bytea
 * in some multi-join queries, which citext has no {@code =} operator for
 * (postgresql.org/docs — citext only defines operators against text/varchar).
 */
public class CitextJdbcType implements JdbcType {

    public static final CitextJdbcType INSTANCE = new CitextJdbcType();

    @Override
    public int getJdbcTypeCode() {
        return SqlTypes.OTHER;
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return new ValueBinder<>() {
            @Override
            public void bind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                if (value == null) {
                    st.setNull(index, Types.VARCHAR);
                } else {
                    st.setString(index, javaType.unwrap(value, String.class, options));
                }
            }

            @Override
            public void bind(CallableStatement st, X value, String name, WrapperOptions options) throws SQLException {
                if (value == null) {
                    st.setNull(name, Types.VARCHAR);
                } else {
                    st.setString(name, javaType.unwrap(value, String.class, options));
                }
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return new ValueExtractor<>() {
            @Override
            public X extract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                return javaType.wrap(rs.getString(paramIndex), options);
            }

            @Override
            public X extract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return javaType.wrap(statement.getString(index), options);
            }

            @Override
            public X extract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return javaType.wrap(statement.getString(name), options);
            }
        };
    }
}
