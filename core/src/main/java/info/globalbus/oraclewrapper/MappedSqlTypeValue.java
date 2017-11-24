package info.globalbus.oraclewrapper;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import oracle.sql.ARRAY;
import oracle.sql.ArrayDescriptor;
import oracle.sql.STRUCT;
import oracle.sql.StructDescriptor;
import org.springframework.jdbc.core.support.AbstractSqlTypeValue;

@RequiredArgsConstructor
@SuppressWarnings("unchecked")
class MappedSqlTypeValue extends AbstractSqlTypeValue {
    private final List<ReflectionSqlTypeValue.MethodFieldWrapper> fields;
    private final InstantiatorWrapper instantiatorWrapper;
    private final Object object;


    @Override
    public Object createTypeValue(Connection con, int sqlType, String typeName) throws SQLException {
        List<Object> values = fields.stream().map(wrapper -> {
            try {
                Object value = instantiatorWrapper.valueToDatabase(wrapper.getMethod().invoke(object));
                if (value == null) {
                    return null;
                }
                if (wrapper.getListParams() == null) {
                    return getObject(con, sqlType, wrapper.getMethod().getReturnType(), value);
                } else {
                    List<Object> arrayElements = new ArrayList<>(((List) value).size());
                    for (Object obj : (List) value) {
                        Object element = instantiatorWrapper.valueToDatabase(obj);
                        arrayElements.add(getObject(con, sqlType, wrapper.getListParams().getGenericType(),
                            element));
                    }
                    return createArray(con, wrapper.getListParams().getTypeName(), arrayElements.toArray());
                }
            } catch (IllegalAccessException | InvocationTargetException | SQLException ex) {
                throw new ProcedureWrapperException("Error on serialization to database", ex);
            }
        }).collect(Collectors.toList());
        final StructDescriptor desc = instantiatorWrapper.getInstantiatorCache().getStructFromCache(typeName, con);
        return new STRUCT(desc, instantiatorWrapper.getInstantiatorCache().getDummyConnection(), values.toArray());
    }

    private static Object getObject(Connection con, int sqlType, Class type, Object value) throws SQLException {
        if (value instanceof MappedSqlTypeValue) {
            return ((MappedSqlTypeValue) value).createTypeValue(con, sqlType, SqlStructParameter.getTypeName(type));
        }
        return value;
    }

    private Object createArray(Connection con, String typeName, Object[] values) throws SQLException {
        final ArrayDescriptor desc = instantiatorWrapper.getInstantiatorCache().getArrayFromCache(typeName, con);
        return new ARRAY(desc, instantiatorWrapper.getInstantiatorCache().getDummyConnection(), values);
    }
}
