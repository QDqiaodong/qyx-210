package com.example.seatstats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SeatStatsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeatStatsApplication.class, args);
    }
}