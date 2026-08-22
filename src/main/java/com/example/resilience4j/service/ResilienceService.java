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
    private static String remoteServiceURL = "http://localhost:8080/test";
    private static String retryURL = "http://localhost:8090/retry";
    private static String remoteServiceDown = "The remote service is currently unavailable. Please try again after some time.";

    @CircuitBreaker(name = "testCircuitBreaker", fallbackMethod = "fallbackForCircuitBreaker")
    public String executeRemoteCall() {
        logTimeDuration();
        return restTemplate.getForObject(remoteServiceURL, String.class);
    }

    public String fallbackForCircuitBreaker(Throwable throwable) {
        return restTemplate.getForObject(retryURL, String.class);
    }

    @Retry(name = "testRetry", fallbackMethod = "fallbackForRetry")
    public String doRetryRemoteCall() {
        logTimeDuration();
        return restTemplate.getForObject(remoteServiceURL, String.class);
    }

    public String fallbackForRetry(Exception e) {
        return remoteServiceDown;
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
