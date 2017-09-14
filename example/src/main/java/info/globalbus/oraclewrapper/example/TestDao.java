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
public class TestDao {

    private static final String PROCEDURE_NAME = "test";
    private final JdbcTemplate template;
    private final InstantiatorWrapper instantion;
    private ProcedureWrapperBuilder<Response>.LazyInitializer wrapper;

    public Response get(Complex input) {
        return wrapper.get().mapObject(input);
    }

    @PostConstruct
    void init() {
        ProcedureWrapperBuilder<Response> wrapperBuilder = new ProcedureWrapperBuilder<>();
        wrapper = wrapperBuilder.jdbcTemplate(template)
            .instantiatorWrapper(instantion)
            .procedureName(PROCEDURE_NAME)
            .clazz(Response.class)
            .parameter(SqlStructParameter.createIn(INPUT_PARAM, Complex.class))
            .parameter(SqlStructParameter.createOut(OUTPUT_PARAM, Response.class))
            .build();
    }
}
