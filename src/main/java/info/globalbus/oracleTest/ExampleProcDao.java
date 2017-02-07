package info.globalbus.oracleTest;

import info.globalbus.oracleTest.OracleProcedureWrapper.StoredProcedureWrapper;
import lombok.SneakyThrows;
import org.dalesbred.internal.instantiation.InstantiatorProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static info.globalbus.oracleTest.OracleProcedureWrapper.StoredProcedureWrapper.OUTPUT_PARAM_NAME;

/**
 * Created by globalbus on 06.02.17.
 */
@Repository
public class ExampleProcDao {

    private StoredProcedureWrapper wrapper;

    @Autowired
    void init(JdbcTemplate template, InstantiatorProvider instantion) throws SQLException {
        wrapper = new StoredProcedureWrapper(template, "example_proc", instantion);
        wrapper.declareParameters(
                Stream.of(new SqlOutParameter(OUTPUT_PARAM_NAME, Types.ARRAY, "array_of_complex".toUpperCase()),
        new SqlParameter("input", Types.STRUCT, "Complex".toUpperCase())).collect(Collectors.toList()));
        wrapper.registerReflectiveConversion(Complex.class, "Complex".toUpperCase());
    }
    List<Complex> getList(Complex input){
        return wrapper.mapList(Complex.class, input);
    }
}
