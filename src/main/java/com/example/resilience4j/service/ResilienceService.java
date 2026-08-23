package com.example.resilience4j.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Service
public class ResilienceService {

    @Autowired
    private RestTemplate restTemplate;

    private static int retryCount = 1;
    private static long lastInvocationTime = -1;
    private static String remoteServiceURL = "http://localhost:8080/test";
    private static String retryURL = "http://localhost:8090/retry";
    private static String remoteServiceDownMsg = "The remote service is currently unavailable. Please try again after some time.";
    private static String bulkheadCallsApiUrl = "http://localhost:8090/doBulkheadCalls";
    private static String reteLimitTestApiURL = "http://localhost:8090/testRateLimit";

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

    public String fallbackForRetry(String id, Throwable e) {
        return remoteServiceDownMsg;
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

    @Bulkhead(name = "testBulkHeadSemaphore", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackForBulkheadSemaphore")
    public String bulkheadSemaphore() {
        System.out.println("Bulkhead semaphore");
        ResponseEntity<String> response = restTemplate.getForEntity(bulkheadCallsApiUrl, String.class);
        System.out.println("Thread: " + Thread.currentThread().getName());
        return response.getBody();
    }

    public ResponseEntity<String> fallbackForBulkheadSemaphore(String id, Throwable e) {
        return new ResponseEntity<>("Semaphore::Too many requests", HttpStatus.TOO_MANY_REQUESTS);
    }

    public String doBulkheadCalls() {
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "BulkheadCall Success!";
    }

    @Bulkhead(name = "testBulkheadThreadPool", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "fallbackForBulkheadThreadPool")
    public CompletableFuture<ResponseEntity<String>> bulkheadThreadPool() {
        System.out.println("Bulkhead thread pool");
        return CompletableFuture.supplyAsync(() -> {
            ResponseEntity<String> response = restTemplate.getForEntity(bulkheadCallsApiUrl, String.class);
            System.out.println("Thread: " + Thread.currentThread().getName());
            System.out.println("response: " + response.getBody());
            return ResponseEntity.ok(response.getBody());
        });
    }

    public CompletableFuture<ResponseEntity<String>> fallbackForBulkheadThreadPool(Throwable throwable) {
        return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("bulkheadThreadPool::Too Many Requests."));
    }

    @RateLimiter(name = "testRateLimiter", fallbackMethod = "fallbackForRateLimiter")
    public String doRateLimit() {
        System.out.println("RateLimit API Call");
        ResponseEntity<String> response = restTemplate.getForEntity(reteLimitTestApiURL, String.class);
        return response.getBody();
    }

    public ResponseEntity<String> fallbackForRateLimiter(String id, Exception e) {
        return new ResponseEntity<>("Too Many Requests", HttpStatus.TOO_MANY_REQUESTS);
    }
}
