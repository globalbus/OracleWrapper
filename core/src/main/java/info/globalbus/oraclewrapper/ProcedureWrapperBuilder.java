package info.globalbus.oraclewrapper;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.util.Assert;

public class ProcedureWrapperBuilder<T> {
    private final BuilderData data = new BuilderData();

    public ProcedureWrapperBuilder<T> jdbcTemplate(JdbcTemplate jdbcTemplate) {
        data.jdbcTemplate = jdbcTemplate;
        return this;
    }

    public ProcedureWrapperBuilder<T> instantiatorWrapper(InstantiatorWrapper instantiatorWrapper) {
        data.instantiatorWrapper = instantiatorWrapper;
        return this;
    }

    public ProcedureWrapperBuilder<T> procedureName(String name) {
        data.procedureName = name;
        return this;
    }

    public ProcedureWrapperBuilder<T> clazz(Class<T> clazz) {
        data.clazz = clazz;
        return this;
    }

    public ProcedureWrapperBuilder<T> initializer(Function<BuilderData, StoredProcedureWrapper<T>> initializer) {
        data.initializer = initializer;
        return this;
    }

    public ProcedureWrapperBuilder<T> parameter(SqlParameter parameter) {
        data.parameters.add(parameter);
        return this;
    }

    public LazyInitializer build() {
        data.validate();
        return new LazyInitializer(data);
    }

    @Getter
    public class BuilderData {
        String procedureName;
        LinkedList<SqlParameter> parameters = new LinkedList<>();
        Function<BuilderData, StoredProcedureWrapper<T>> initializer;
        JdbcTemplate jdbcTemplate;
        InstantiatorWrapper instantiatorWrapper;
        Class<T> clazz;

        void validate() {
            Assert.hasText(procedureName, "procedureName is required parameter");
            Assert.notNull(jdbcTemplate, "jdbcTemplate is required parameter");
            Assert.notNull(instantiatorWrapper, "instantiatorWrapper is required parameter");
            if (clazz == null) {
                Assert.notNull(initializer, "initializer or clazz must be provided");
            }
        }
    }

    @RequiredArgsConstructor
    public class LazyInitializer implements Lazy<ProcedureCaller<T>> {
        final BuilderData data;
        ProcedureCaller<T> procedureCaller;

        @Override
        public synchronized ProcedureCaller<T> get() {
            if (procedureCaller == null) {
                procedureCaller = init();
            }
            return procedureCaller;
        }

        private ProcedureCaller<T> init() {
            if (data.initializer == null) {
                data.initializer = d -> new StoredProcedureWrapper<>(data.jdbcTemplate, data.procedureName,
                    data.instantiatorWrapper, data.clazz);
            }
            StoredProcedureWrapper<T> wrapper = data.initializer.apply(data);
            try {
                wrapper.declareParameters(data.parameters);
            } catch (SQLException ex) {
                throw new ProcedureWrapperException("Cannot initialize wrapper", ex);
            }
            return wrapper;
        }
    }
}
