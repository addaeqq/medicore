package com.medicore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling // DD-08: outbox drain + reminder scan
// Repositories are declared as nested interfaces inside com.medicore.repo.Repositories,
// which Spring Data skips unless nested repository interfaces are explicitly considered.
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(
    basePackages = "com.medicore.repo", considerNestedRepositories = true)
public class MedicoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedicoreApplication.class, args);
    }
}
