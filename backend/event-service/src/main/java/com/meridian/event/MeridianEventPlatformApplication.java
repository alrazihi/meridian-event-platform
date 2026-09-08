package com.meridian.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MeridianEventPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeridianEventPlatformApplication.class, args);
    }
}
