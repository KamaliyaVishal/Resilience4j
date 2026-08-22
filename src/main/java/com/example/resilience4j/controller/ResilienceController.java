package com.example.resilience4j.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ResilienceController {

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping("/test")
    @CircuitBreaker(name = "testCircuitBreaker", fallbackMethod = "fallbackCall")
    public ResponseEntity<String> doCircuitBreaker() {
        String dummyApiURL = "http://localhost:8080/test";
        ResponseEntity<String> response = restTemplate.getForEntity(dummyApiURL, String.class);
        return ResponseEntity.ok(response.getBody());
    }

    public ResponseEntity<String>  fallbackCall(Throwable throwable) {
        return ResponseEntity.ok("dummyApi is down!!!");
    }

}
