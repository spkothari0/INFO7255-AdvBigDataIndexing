package com.neu.AdvBigDataIndexing;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRabbit
public class AdvBigDataIndexingApplication {

    public static final String queueName = "indexing-queue";

    public static void main(String[] args) {
        SpringApplication.run(AdvBigDataIndexingApplication.class, args);
    }

}
