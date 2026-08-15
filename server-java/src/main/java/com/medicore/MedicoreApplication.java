package com.medicore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling // DD-08: outbox drain + reminder scan
public class MedicoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedicoreApplication.class, args);
    }
}
