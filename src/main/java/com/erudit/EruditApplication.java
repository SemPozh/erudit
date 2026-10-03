package com.erudit;

import com.erudit.user.config.VerificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(VerificationProperties.class)
public class EruditApplication {

    public static void main(String[] args) {
        SpringApplication.run(EruditApplication.class, args);
    }
}