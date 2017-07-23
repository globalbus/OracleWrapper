package info.globalbus.oraclewrapper.example;

import javax.sql.DataSource;
import org.dalesbred.dialect.OracleDialect;
import org.dalesbred.internal.instantiation.InstantiatorProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;

@EnableAutoConfiguration
@ComponentScan
public class Application {

    public static void main(String[] args) {
        System.setProperty("spring.devtools.remote.debug.local-port", "5005");
        SpringApplication.run(Application.class, args);
    }

    @Bean
    InstantiatorProvider instantiatorProvider() {
        return new InstantiatorProvider(new OracleDialect());
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
