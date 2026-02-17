package com.dsip.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootApplication(exclude = {HibernateJpaAutoConfiguration.class})
@MapperScan("com.dsip.backend")
public class DsipBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DsipBackendApplication.class, args);
    }
}
