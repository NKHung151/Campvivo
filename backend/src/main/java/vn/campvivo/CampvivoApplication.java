package vn.campvivo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CampvivoApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampvivoApplication.class, args);
    }
}
