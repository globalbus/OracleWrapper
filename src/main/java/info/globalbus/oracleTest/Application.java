package info.globalbus.oracleTest;

import org.dalesbred.dialect.OracleDialect;
import org.dalesbred.internal.instantiation.InstantiatorProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@EnableAutoConfiguration
@ComponentScan
public class Application extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }
    public static void main(String[] args) {
        System.setProperty("spring.devtools.remote.debug.local-port", "5005");
        SpringApplication.run(Application.class, args);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource ds){
        return new JdbcTemplate(ds);
    }
    @Bean
    InstantiatorProvider instantiatorProvider(){
        return new InstantiatorProvider(new OracleDialect());
    }
}
