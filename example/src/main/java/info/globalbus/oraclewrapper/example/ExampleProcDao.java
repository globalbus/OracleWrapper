package info.globalbus.oraclewrapper.example;

import info.globalbus.oraclewrapper.InstantiatorWrapper;
import info.globalbus.oraclewrapper.ProcedureWrapperBuilder;
import info.globalbus.oraclewrapper.SqlStructParameter;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import static info.globalbus.oraclewrapper.ProcedureCaller.INPUT_PARAM;
import static info.globalbus.oraclewrapper.ProcedureCaller.OUTPUT_PARAM;

/**
 * Created by globalbus on 06.02.17.
 */
@Repository
@RequiredArgsConstructor
public class ExampleProcDao {

    private static final String PROCEDURE_NAME = "example_proc";
    private final JdbcTemplate template;
    private final InstantiatorWrapper instantion;
    private ProcedureWrapperBuilder<Complex>.LazyInitializer wrapper;

    public List<Complex> getList(Complex input) {
        return wrapper.get().mapList(input);
    }

    @PostConstruct
    void init() {
        ProcedureWrapperBuilder<Complex> wrapperBuilder = new ProcedureWrapperBuilder<>();
        wrapper = wrapperBuilder.jdbcTemplate(template)
            .instantiatorWrapper(instantion)
            .procedureName(PROCEDURE_NAME)
            .clazz(Complex.class)
            .parameter(SqlStructParameter.createOutArray(OUTPUT_PARAM, Complex.class,
                "array_of_complex"))
            .parameter(SqlStructParameter.createIn(INPUT_PARAM, Complex.class))
            .build();
    }
}
