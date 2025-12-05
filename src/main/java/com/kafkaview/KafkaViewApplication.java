package com.kafkaview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KafkaViewApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaViewApplication.class, args);
    }

}
