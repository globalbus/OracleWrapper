package info.globalbus.oraclewrapper;

import java.sql.Types;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.util.StringUtils;

@Slf4j
@UtilityClass
public class SqlStructParameter {
    public SqlParameter createIn(String name, Class<?> clazz) {
        return new SqlStructInParameter(name, getTypeName(clazz), clazz);
    }

    public SqlParameter createOut(String name, Class<?> clazz) {
        return new SqlStructOutParameter(name, getTypeName(clazz), clazz);
    }

    public SqlParameter createOutArray(String name, Class<?> clazz, String arrayTypeName) {
        return new SqlStructOutParameter(name, getTypeName(clazz), clazz, arrayTypeName.toUpperCase());
    }

    public String getTypeName(Class<?> clazz) {
        OracleStruct annotation = clazz.getAnnotation(OracleStruct.class);
        String typeName;
        if (annotation == null) {
            log.warn("There is no OracleStruct annotation on struct class");
        }
        if (annotation == null || StringUtils.isEmpty(annotation.value())) {
            typeName = clazz.getSimpleName().toUpperCase();
        } else {
            typeName = annotation.value();
        }
        return typeName;
    }

    static final class SqlStructInParameter extends SqlParameter implements SqlClassHolder {
        @Getter
        private final Class<?> clazz;
        @Getter
        private final String structTypeName;

        private SqlStructInParameter(String name, String typeName, Class<?> clazz) {
            super(name, Types.STRUCT, typeName);
            this.clazz = clazz;
            this.structTypeName = typeName;
        }
    }

    static final class SqlStructOutParameter extends SqlOutParameter implements SqlClassHolder {
        @Getter
        private final Class<?> clazz;
        @Getter
        private final String structTypeName;

        private SqlStructOutParameter(String name, String typeName, Class<?> clazz) {
            super(name, Types.STRUCT, typeName);
            this.clazz = clazz;
            this.structTypeName = typeName;
        }

        private SqlStructOutParameter(String name, String typeName, Class<?> clazz, String arrayTypeName) {
            super(name, Types.ARRAY, arrayTypeName);
            this.clazz = clazz;
            this.structTypeName = typeName;
        }
    }
}
