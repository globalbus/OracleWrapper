package info.globalbus.oracleTest.OracleProcedureWrapper;

import oracle.jdbc.OracleArray;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleStruct;
import oracle.sql.STRUCT;
import oracle.sql.StructDescriptor;
import org.dalesbred.internal.instantiation.InstantiatorProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.StoredProcedure;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by globalbus on 06.02.17.
 */
public class StoredProcedureWrapper {
    private final InternalStoredProcedure internal;
    private InstantiatorProvider instantiatorProvider;
    public final static String OUTPUT_PARAM_NAME = "output";
    private InstantiatorCache instantiatorCache;

    public void declareParameters(List<SqlParameter> sqlParameters) {
        if (internal.isCompiled())
            throw new IllegalStateException("Object cannot be reinitialized");
        sqlParameters.forEach(internal::declareParameter);
        internal.compile();
    }

    private static class InternalStoredProcedure extends StoredProcedure {
        InternalStoredProcedure(JdbcTemplate jdbcTemplate, String procName) {
            super(jdbcTemplate, procName);
        }
    }

    public StoredProcedureWrapper(JdbcTemplate jdbcTemplate, String procName, InstantiatorProvider instantiatorProvider) {
        this.internal = new InternalStoredProcedure(jdbcTemplate, procName);
        this.instantiatorProvider = instantiatorProvider;
        this.instantiatorCache = new InstantiatorCache(instantiatorProvider);
    }

    public <T> void registerReflectiveConversion(Class<T> clazz, String typeName) throws SQLException {
        try (OracleConnection con = getOracleConnection()) {
            StructDescriptor desc = new StructDescriptor(typeName, con);
            ReflectionSqlTypeValue<T> reflectionSqlTypeValue = new ReflectionSqlTypeValue<>(clazz, desc, instantiatorProvider);
            instantiatorProvider.getTypeConversionRegistry().registerConversionToDatabase(clazz, reflectionSqlTypeValue::getSqlTypeValue);
        } finally {
            getOracleConnection().close();
        }
    }

    public <T> List<T> mapList(Class<T> outputClass, Object... input) {
        List<Object> mappedObjects = Stream.of(input).map(instantiatorProvider::valueToDatabase).collect(Collectors.toList());
        Map<String, Object> results = internal.execute(mappedObjects.toArray());
        Object output = results.get(OUTPUT_PARAM_NAME);
        return getOutputList(outputClass, output);
    }

    public <T> T mapObject(Class<T> outputClass, Object... input) {
        List<Object> mappedObjects = Stream.of(input).map(instantiatorProvider::valueToDatabase).collect(Collectors.toList());
        Map<String, Object> results = internal.execute(mappedObjects.toArray());
        Object output = results.get(OUTPUT_PARAM_NAME);
        return getOutputObject(outputClass, output);
    }

    protected <T> List<T> getOutputList(Class<T> outputClass, Object output) {
        List<T> ret = new ArrayList<>();
        try {
            if (output instanceof OracleArray) {
                Object[] structs = (Object[]) ((OracleArray) output).getArray();
                InstantiatorCache.InstantiatorEntry<T> entry = instantiatorCache.get(outputClass);
                if (entry == null)
                    entry = instantiatorCache.add(outputClass, (STRUCT) structs[0]);
                for (Object obj : structs) {
                    Object[] attr = ((STRUCT) obj).getAttributes();
                    T javaObject = entry.instantiate(attr);
                    if (javaObject != null)
                        ret.add(javaObject);
                }
            } else
                throw new RuntimeException("Passed object is not a OracleArray");
        } catch (Exception e) {
            throw new RuntimeException("Cannot read OracleArray", e);
        }
        return ret;
    }


    protected <T> T getOutputObject(Class<T> outputClass, Object output) {
        try {
            if (output instanceof OracleStruct) {
                STRUCT struct = (STRUCT) output;
                InstantiatorCache.InstantiatorEntry<T> entry = instantiatorCache.get(outputClass);
                if (entry == null)
                    entry = instantiatorCache.add(outputClass, struct);
                Object[] attr = struct.getAttributes();
                T javaObject = entry.instantiate(attr);
                if (javaObject != null)
                    return javaObject;
            } else
                throw new RuntimeException("Passed object is not a OracleStruct");
        } catch (Exception e) {
            throw new RuntimeException("Cannot read OracleStruct", e);
        }
        return null;
    }

    protected OracleConnection getOracleConnection() throws SQLException {
        return internal.getJdbcTemplate().getDataSource().getConnection().unwrap(OracleConnection.class);
    }
}
