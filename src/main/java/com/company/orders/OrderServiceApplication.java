package com.company.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point.
 *
 * <p>
 * Virtual threads are enabled via application.yml: spring.threads.virtual.enabled=true
 *
 * <p>
 * This single property makes Spring Boot 4 use virtual threads for: - Tomcat request handling (each
 * HTTP request runs on a virtual thread) - @Async task execution - @Scheduled tasks - JDK
 * HttpClient
 *
 * <p>
 * With Java 25 LTS + virtual threads, this service can handle tens of thousands of concurrent
 * requests with minimal memory overhead.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
