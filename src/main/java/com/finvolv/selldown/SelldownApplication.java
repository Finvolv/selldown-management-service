package com.finvolv.selldown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.finvolv.selldown","com.finvolv.iam.dependency"})
public class SelldownApplication {

    public static void main(String[] args) {
        SpringApplication.run(SelldownApplication.class, args);
    }
}


