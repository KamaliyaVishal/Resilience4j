package com.example.resilience4j.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ResilienceService {

    @Autowired
    private RestTemplate restTemplate;

    private static int retryCount = 1;
    private static long lastInvocationTime = -1;

    @CircuitBreaker(name = "testCircuitBreaker", fallbackMethod = "fallbackCall")
    public String executeRemoteCall() {
        logTimeDuration();
        String dummyApiURL = "http://localhost:8080/test";
        return restTemplate.getForObject(dummyApiURL, String.class);
    }

    public String fallbackCall(Throwable throwable) {
        String retryApiCall = "http://localhost:8090/retry";
        return restTemplate.getForObject(retryApiCall, String.class);
    }

    @Retry(name = "testRetry", fallbackMethod = "fallbackForRetry")
    public String doRetryRemoteCall() {
        logTimeDuration();
        String dummyApiURL = "http://localhost:8080/test";
        return restTemplate.getForObject(dummyApiURL, String.class);
    }

    public String fallbackForRetry(Exception e) {
        return "dummyApi is down";
    }


    private void logTimeDuration() {
        long currentTime = System.currentTimeMillis();
        if (lastInvocationTime != -1) {
            long durationMillis = currentTime - lastInvocationTime;
            System.out.println("Retry count: " + retryCount++);
            System.out.println("Time since last call: " + durationMillis + " Milliseconds");
        } else {
            System.out.println("Initial Call Attempted...");
        }
        lastInvocationTime = currentTime;
    }

}
