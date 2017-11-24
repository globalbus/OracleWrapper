package info.globalbus.oraclewrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleConnection;
import oracle.sql.StructDescriptor;
import org.dalesbred.internal.instantiation.NamedTypeList;
import org.dalesbred.internal.jdbc.ResultSetUtils;
import org.springframework.jdbc.core.SqlTypeValue;

import static info.globalbus.oraclewrapper.internal.util.ReflectionUtils.findField;
import static info.globalbus.oraclewrapper.internal.util.ReflectionUtils.findGetterOrSetter;

/**
 * Class for caching Java to Database conversion.
 */
@Slf4j
class ReflectionSqlTypeValue<T> {
    private final Class<T> clazz;
    private final InstantiatorWrapper instantiatorWrapper;
    private final List<MethodFieldWrapper> fields = new LinkedList<>();

    ReflectionSqlTypeValue(Class<T> clazz, OracleConnection con, String typeName,
        InstantiatorWrapper instantiatorWrapper) throws SQLException {
        final StructDescriptor desc = instantiatorWrapper.getInstantiatorCache().getStructFromCache(typeName, con);
        this.clazz = clazz;
        this.instantiatorWrapper = instantiatorWrapper;
        mapFields(desc, con);
    }

    SqlTypeValue getSqlTypeValue(Object obj) {
        return new MappedSqlTypeValue(fields, instantiatorWrapper, obj);
    }

    private void mapFields(StructDescriptor desc, OracleConnection con) throws SQLException {
        NamedTypeList fieldList = instantiatorWrapper.getInstantiatorCache().inConnection(desc, con, () ->
            ResultSetUtils.getTypes(desc.getMetaData()));
        for (String f : fieldList.getNames()) {
            Method value = findGetterOrSetter(clazz, f, true).orElseThrow(() ->
                new ProcedureWrapperException("Property " + f + " not found in object " + clazz.getName()));
            Class<?> innerType = value.getReturnType();
            ListParams listParams = null;
            if (innerType.isAssignableFrom(List.class)) {
                Field field = findField(clazz, f)
                    .orElseThrow(() -> new ProcedureWrapperException("Field not found " + f));
                String arrayName = Optional.ofNullable(field.getAnnotation(OracleArray.class)).map(OracleArray::value)
                    .orElse(null);
                if (arrayName != null) {
                    ParameterizedType listType = (ParameterizedType) field.getGenericType();
                    Class<?> genericType = (Class<?>) (listType).getActualTypeArguments()[0];
                    registerType(con, genericType);
                    listParams = new ListParams(arrayName, genericType);
                } else {
                    throw new ProcedureWrapperException("Cannot find OracleArray annotation");
                }
            }
            registerType(con, innerType);
            fields.add(new MethodFieldWrapper(value, listParams));
        }
    }

    private void registerType(OracleConnection con, Class<?> innerType) throws SQLException {
        if (innerType.getAnnotation(OracleStruct.class) != null && !instantiatorWrapper.getInstantiatorCache()
            .isKnownInput(innerType)) {
            ReflectionSqlTypeValue<?> reflectionSqlTypeValue = new ReflectionSqlTypeValue<>(innerType, con,
                SqlStructParameter.getTypeName(innerType), instantiatorWrapper);
            instantiatorWrapper.registerConversionToDatabase(innerType, reflectionSqlTypeValue::getSqlTypeValue);
        }
    }

    @Value
    @FieldDefaults(level = AccessLevel.PRIVATE)
    static class MethodFieldWrapper {
        Method method;
        ListParams listParams;
    }

    @Value
    @FieldDefaults(level = AccessLevel.PRIVATE)
    static class ListParams {
        String typeName;
        Class<?> genericType;
    }
}
