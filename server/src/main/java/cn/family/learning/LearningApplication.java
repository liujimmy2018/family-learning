package cn.family.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(excludeName="org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
public class LearningApplication {
    public static void main(String[] args) {
        org.springframework.context.ConfigurableApplicationContext context=SpringApplication.run(LearningApplication.class,args);
        String role=context.getEnvironment().getProperty("app.role");
        if("admin".equals(role)||"migrate".equals(role))SpringApplication.exit(context,()->0);
    }
}
