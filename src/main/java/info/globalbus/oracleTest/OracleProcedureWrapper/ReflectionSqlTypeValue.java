package info.globalbus.oracleTest.OracleProcedureWrapper;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oracle.sql.STRUCT;
import oracle.sql.StructDescriptor;
import org.dalesbred.annotation.DalesbredIgnore;
import org.dalesbred.conversion.TypeConversionRegistry;
import org.dalesbred.internal.instantiation.InstantiationFailureException;
import org.dalesbred.internal.instantiation.InstantiatorProvider;
import org.dalesbred.internal.instantiation.NamedTypeList;
import org.dalesbred.internal.jdbc.ResultSetUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.support.AbstractSqlTypeValue;

import javax.validation.constraints.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.reflect.Modifier.isPublic;
import static org.dalesbred.internal.utils.StringUtils.isEqualIgnoringCaseAndUnderscores;

/**
 * Created by globalbus on 06.02.17.
 */
@Slf4j
class ReflectionSqlTypeValue<T> {
    private final Class<T> clazz;
    private final InstantiatorProvider instantiatorProvider;
    private List<Method> fields;

    public ReflectionSqlTypeValue(Class<T> clazz, StructDescriptor desc, InstantiatorProvider instantiatorProvider) throws SQLException {
        this.clazz=clazz;
        this.instantiatorProvider=instantiatorProvider;
        mapFields(desc);
    }

    public SqlTypeValue getSqlTypeValue(T obj){
        return new MappedSqlTypeValue(obj);
    }
    private void mapFields(StructDescriptor desc) throws SQLException {
        fields = new LinkedList<>();
        NamedTypeList fieldList = ResultSetUtils.getTypes(desc.getMetaData());
        for (String f : fieldList.getNames())
            fields.add(findGetterOrSetter(clazz, f, true).orElseThrow(RuntimeException::new));
    }


    @NotNull
    private Optional<Method> findGetterOrSetter(@NotNull Class<?> cl, @NotNull String propertyName, boolean getter) {
        String methodName = (getter ? "get" : "set") + propertyName;
        int parameterCount = getter ? 0 : 1;
        Method result = null;

        for (Method method : cl.getMethods()) {
            if (isPublic(method.getModifiers())
                    && isEqualIgnoringCaseAndUnderscores(methodName, method.getName())
                    && method.getParameterCount() == parameterCount
                    && !method.isAnnotationPresent(DalesbredIgnore.class)) {
                if (result != null)
                    throw new InstantiationFailureException("Conflicting accessors for property: " + result + " - " + propertyName);
                result = method;
            }
        }

        return Optional.ofNullable(result);
    }
    class MappedSqlTypeValue extends AbstractSqlTypeValue {
        private final Object object;

        MappedSqlTypeValue(Object object) {
            this.object = object;
        }

        @Override
        protected Object createTypeValue(Connection con, int sqlType, String typeName) throws SQLException {
            StructDescriptor desc = new StructDescriptor(typeName.toUpperCase(), con);
            List<Object> values = fields.stream().map(new Function<Method, Object>() {
                @Override
                @SneakyThrows
                public Object apply(Method method) {
                    return instantiatorProvider.valueToDatabase(method.invoke(object));
                }

        }).collect(Collectors.toList());
            return new STRUCT(desc, con, values.toArray());
        }
    }
}
