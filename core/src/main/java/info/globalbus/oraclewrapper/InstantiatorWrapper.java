package info.globalbus.oraclewrapper;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleArray;
import oracle.jdbc.OracleStruct;
import oracle.sql.STRUCT;
import org.dalesbred.conversion.TypeConversionRegistry;
import org.dalesbred.dialect.OracleDialect;
import org.dalesbred.internal.instantiation.InstantiatorProvider;

/**
 * Instantiator wrapper for handling database to Java conversions. Designed to be used as single instance,
 * with shared cache between StoredProcedureWrapper instances.
 */
@Slf4j
public class InstantiatorWrapper {
    @Getter
    private final InstantiatorProvider instantiatorProvider;
    @Getter
    private final InstantiatorCache instantiatorCache;

    public InstantiatorWrapper() {
        instantiatorProvider = new InstantiatorProvider(new OracleDialect());
        instantiatorCache = new InstantiatorCache(instantiatorProvider);
        TypeConversionRegistry typeConversionRegistry = instantiatorProvider.getTypeConversionRegistry();
        typeConversionRegistry.registerConversionFromDatabase(OracleArray.class,
            List.class, this::getOutputList);
        typeConversionRegistry.registerConversionFromDatabase(OracleStruct.class, Void.class, v -> null);
    }

    //register custom conversions
    public <S> void registerConversionToDatabase(Class<S> source, Function<S, ?> conversion) {
        instantiatorProvider.getTypeConversionRegistry().registerConversionToDatabase(source, conversion);
        instantiatorCache.setKnownInput(source);
    }

    //register custom conversions
    public <S> void registerListConversionToDatabase(Class<S> source, Function<S, ?> conversion) {
        instantiatorProvider.getTypeConversionRegistry().registerConversionToDatabase(source, conversion);
        instantiatorCache.setKnownInput(source);
    }

    public <T> List<T> getOutputList(Class<T> outputClass, Object output) {
        try {
            if (output instanceof OracleArray) {
                Object[] structs = (Object[]) ((OracleArray) output).getArray();
                if (structs.length == 0) {
                    return Collections.emptyList();
                }
                return readArray(outputClass, structs);

            } else {
                throw new ProcedureWrapperException("Passed object is not a OracleArray");
            }
        } catch (Exception ex) {
            throw new ProcedureWrapperException("Cannot read OracleArray", ex);
        }
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    private <T> List<T> getOutputList(Object output) {
        if (output instanceof OracleArray) {
            Object[] structs = (Object[]) ((OracleArray) output).getArray();
            if (structs.length == 0) {
                return Collections.emptyList();
            }
            Class<T> type = (Class<T>) instantiatorCache.getClassByName(((STRUCT) structs[0]).getDescriptor()
                .getSQLName());
            return getOutputList(type, output);
        } else {
            throw new ProcedureWrapperException("Passed object is not a OracleArray");
        }
    }

    private <T> List<T> readArray(Class<T> outputClass, Object[] structs) throws SQLException {
        List<T> ret = new ArrayList<>();
        InstantiatorCache.InstantiatorEntry<T> entry = instantiatorCache.get(outputClass);
        if (entry == null) {
            entry = instantiatorCache.add(outputClass, ((STRUCT) structs[0]).getDescriptor());
        }
        for (Object obj : structs) {
            Object[] attr = ((STRUCT) obj).getAttributes();
            T javaObject = entry.instantiate(attr);
            if (javaObject != null) {
                ret.add(javaObject);
            }
        }
        return ret;
    }

    public <T> T getOutputObject(Class<T> outputClass, Object output) {
        try {
            if (output instanceof OracleStruct) {
                STRUCT struct = (STRUCT) output;
                InstantiatorCache.InstantiatorEntry<T> entry = instantiatorCache.get(outputClass);
                if (entry == null) {
                    entry = instantiatorCache.add(outputClass, struct.getDescriptor());
                }
                Object[] attr = struct.getAttributes();
                T javaObject = entry.instantiate(attr);
                if (javaObject != null) {
                    return javaObject;
                }
            } else if (output == null) {
                return null;
            } else {
                throw new ProcedureWrapperException("Passed object is not a OracleStruct");
            }
        } catch (Exception ex) {
            throw new ProcedureWrapperException("Cannot read OracleStruct", ex);
        }
        return null;
    }

    private <T> void cacheInnerClasses(Class<T> outputClass) throws SQLException {
        Field[] fields = outputClass.getDeclaredFields();
        for (Field field : fields) {
            Class<?> type = field.getType();
            if (isUnknown(type)) {
                registerReflectiveConversionOutput(type);
            }
            if (List.class.isAssignableFrom(type)) {
                ParameterizedType listType = (ParameterizedType) field.getGenericType();
                Class<?> genericType = (Class<?>) (listType).getActualTypeArguments()[0];
                if (isUnknown(genericType)) {
                    registerReflectiveConversionOutput(genericType);
                }
            }
        }
    }

    private boolean isUnknown(Class<?> type) {
        return type.getAnnotation(info.globalbus.oraclewrapper.OracleStruct.class) != null
            && !instantiatorCache.isKnown(type);
    }

    public <T> void registerReflectiveConversionOutput(Class<T> clazz) throws SQLException {
        instantiatorProvider.getTypeConversionRegistry().registerConversionFromDatabase(OracleStruct.class, clazz, v
            -> getOutputObject(clazz, v));
        instantiatorCache.setKnown(clazz);
        cacheInnerClasses(clazz);
    }

    public Object valueToDatabase(Object obj) {
        return this.instantiatorProvider.valueToDatabase(obj);
    }

}
