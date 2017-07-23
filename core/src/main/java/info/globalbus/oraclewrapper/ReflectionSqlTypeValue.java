package info.globalbus.oraclewrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleConnection;
import oracle.sql.STRUCT;
import oracle.sql.StructDescriptor;
import org.dalesbred.internal.instantiation.NamedTypeList;
import org.dalesbred.internal.jdbc.ResultSetUtils;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.support.AbstractSqlTypeValue;

import static info.globalbus.oraclewrapper.internal.util.ReflectionUtils.findGetterOrSetter;

/**
 * Class for caching Java to Database conversion.
 */
@Slf4j
class ReflectionSqlTypeValue<T> {
    private final Class<T> clazz;
    private final InstantiatorWrapper instantiatorWrapper;
    private List<Method> fields;

    ReflectionSqlTypeValue(Class<T> clazz, OracleConnection con, String typeName,
        InstantiatorWrapper instantiatorWrapper) throws SQLException {
        StructDescriptor desc = new StructDescriptor(typeName, con);
        this.clazz = clazz;
        this.instantiatorWrapper = instantiatorWrapper;
        mapFields(desc, con);
    }

    SqlTypeValue getSqlTypeValue(Object obj) {
        return new MappedSqlTypeValue(obj);
    }

    private void mapFields(StructDescriptor desc, OracleConnection con) throws SQLException {
        fields = new LinkedList<>();
        NamedTypeList fieldList = ResultSetUtils.getTypes(desc.getMetaData());
        for (String f : fieldList.getNames()) {
            Method value = findGetterOrSetter(clazz, f, true)
                .orElseThrow(() -> new RuntimeException("Property not found in object " + f));
            Class<?> innerType = value.getReturnType();
            if (innerType.getAnnotation(OracleStruct.class) != null && !instantiatorWrapper.getInstantiatorCache()
                .isKnownInput(innerType)) {
                ReflectionSqlTypeValue<?> reflectionSqlTypeValue = new ReflectionSqlTypeValue<>(innerType, con,
                    SqlStructParameter.getTypeName(innerType), instantiatorWrapper);
                instantiatorWrapper.registerConversionToDatabase(innerType, reflectionSqlTypeValue::getSqlTypeValue);
            }
            fields.add(value);
        }
    }


    class MappedSqlTypeValue extends AbstractSqlTypeValue {
        private final Object object;

        MappedSqlTypeValue(Object object) {
            this.object = object;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object createTypeValue(Connection con, int sqlType, String typeName) throws SQLException {
            List<Object> values = fields.stream().map(method -> {
                try {
                    Object value = instantiatorWrapper.valueToDatabase(method.invoke(object));
                    if (value instanceof ReflectionSqlTypeValue.MappedSqlTypeValue) {
                        return ((MappedSqlTypeValue) value).createTypeValue(con, sqlType, method.getReturnType()
                            .getSimpleName());
                    }
                    return value;
                } catch (IllegalAccessException | InvocationTargetException | SQLException ex) {
                    throw new ProcedureWrapperException("Error on serialization to database", ex);
                }
            }).collect(Collectors.toList());
            StructDescriptor desc = new StructDescriptor(typeName.toUpperCase(), con);
            return new STRUCT(desc, con, values.toArray());
        }
    }
}
