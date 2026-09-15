package com.example.nl;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.nl.mapper")
public class NlApplication {

    public static void main(String[] args) {
        SpringApplication.run(NlApplication.class, args);
    }

}
