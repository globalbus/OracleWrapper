package info.globalbus.oraclewrapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import oracle.jdbc.OracleConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.StoredProcedure;

/**
 * Stored procedure wrapper for Oracle Objects. One object can handle only one Oracle procedure.
 * For procedures which have more than one output parameter, class must be inherit.
 */
public class StoredProcedureWrapper<T> implements ProcedureCaller<T> {
    protected final InternalStoredProcedure internal;
    protected final InstantiatorWrapper instantiatorWrapper;
    protected final Class<T> clazz;

    public StoredProcedureWrapper(JdbcTemplate jdbcTemplate, String procName, InstantiatorWrapper
        instantiatorWrapper, Class<T> clazz) {
        this.internal = new InternalStoredProcedure(jdbcTemplate, procName);
        this.instantiatorWrapper = instantiatorWrapper;
        this.clazz = clazz;
    }

    public StoredProcedureWrapper(ProcedureWrapperBuilder<T>.BuilderData builderData) {
        this.internal = new InternalStoredProcedure(builderData.getJdbcTemplate(), builderData.getProcedureName());
        this.instantiatorWrapper = builderData.getInstantiatorWrapper();
        this.clazz = builderData.getClazz();
    }

    /**
     * Method can be called only once, at initialization.
     *
     * @param sqlParameters list of input and output parameters. Parameters must have a unique identifiers.
     */
    public void declareParameters(List<SqlParameter> sqlParameters) throws SQLException {
        if (internal.isCompiled()) {
            throw new IllegalStateException("Object cannot be reinitialized");
        }
        registerParameters(sqlParameters);
        sqlParameters.forEach(internal::declareParameter);
        internal.compile();
    }

    private void registerParameters(List<SqlParameter> sqlParameters) throws SQLException {
        for (SqlParameter v : sqlParameters) {
            if (v instanceof SqlStructParameter.SqlStructInParameter) {
                SqlClassHolder structParam = (SqlClassHolder) v;
                registerReflectiveConversion(structParam.getClazz(), structParam.getTypeName());
            } else if (v instanceof SqlStructParameter.SqlStructOutParameter) {
                SqlClassHolder structParam = (SqlClassHolder) v;
                registerReflectiveConversionOutput(structParam.getClazz());
            }
        }
    }

    protected static class InternalStoredProcedure extends StoredProcedure {
        InternalStoredProcedure(JdbcTemplate jdbcTemplate, String procName) {
            super(jdbcTemplate, procName);
        }
    }

    /**
     * Register reflective autoconversion from Java Object to Oracle Object.
     *
     * @param clazz    Java class
     * @param typeName Oracle class name (normally visible in uppercase)
     */
    private <R> void registerReflectiveConversion(Class<R> clazz, String typeName) throws SQLException {
        callWithin(con -> {
            try {
                ReflectionSqlTypeValue<R> reflectionSqlTypeValue = new ReflectionSqlTypeValue<>(clazz, con, typeName,
                    instantiatorWrapper);
                instantiatorWrapper.registerConversionToDatabase(clazz, reflectionSqlTypeValue::getSqlTypeValue);
            } catch (SQLException ex) {
                throw new ProcedureWrapperException("Cannot get object from database", ex);
            }
        });
    }

    /**
     * Register reflective autoconversion from Oracle Object to Java Object.
     *
     * @param clazz Java class
     */
    private <R> void registerReflectiveConversionOutput(Class<R> clazz) throws SQLException {
        instantiatorWrapper.registerReflectiveConversionOutput(clazz);
    }

    /**
     * Map procedure output as List of Java Object
     *
     * @param input input parameters for procedure. Order of arguments should be preserved
     * @return List of T
     */
    @Override
    public List<T> mapList(Object... input) {
        List<Object> mappedObjects = Stream.of(input).map(instantiatorWrapper::valueToDatabase)
            .collect(Collectors.toList());
        Map<String, Object> results = internal.execute(mappedObjects.toArray());
        Object output = results.get(OUTPUT_PARAM);
        return instantiatorWrapper.getOutputList(clazz, output);
    }

    /**
     * Map procedure output as Java Object
     *
     * @param input input parameters for procedure. Order of arguments should be preserved
     * @return T
     */
    @Override
    public T mapObject(Object... input) {
        List<Object> mappedObjects = Stream.of(input).map(instantiatorWrapper::valueToDatabase)
            .collect(Collectors.toList());
        Map<String, Object> results = internal.execute(mappedObjects.toArray());
        Object output = results.get(OUTPUT_PARAM);
        return instantiatorWrapper.getOutputObject(clazz, output);
    }

    private void callWithin(Consumer<OracleConnection> consumer) throws SQLException {
        try (Connection datasourceConnection = internal.getJdbcTemplate().getDataSource().getConnection()) {
            OracleConnection con = datasourceConnection.unwrap(OracleConnection.class);
            consumer.accept(con);
        }
    }
}
