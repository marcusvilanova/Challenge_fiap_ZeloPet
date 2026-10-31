package br.com.zelo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ZeloApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZeloApplication.class, args);
    }
}
