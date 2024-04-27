package start;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan(basePackages = {"luonq.mapper"})
@EnableScheduling
@ComponentScan(basePackages = {"luonq.*"})
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        SpringApplication.run(Application.class, args);
    }
}
