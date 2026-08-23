package com.example.resilience4j.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Service
public class ResilienceService {

    @Autowired
    private RestTemplate restTemplate;

    private static int retryCount = 1;
    private static long lastInvocationTime = -1;
    private static final String remoteServiceURL = "http://localhost:8080/test";
    private static final String retryURL = "http://localhost:8090/retry";
    private static final String remoteServiceDownMsg = "The remote service is currently unavailable. Please try again after some time.";
    private static final String mockWaitAPICallURL = "http://localhost:8090/mockWaitAPICall";
    private static final String rateLimitTestApiURL = "http://localhost:8090/mockRemoteAPICall";


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

    public String fallbackForRetry(Throwable throwable) {
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
    public ResponseEntity<String> bulkheadSemaphore() {
        System.out.println("Bulkhead semaphore");
        ResponseEntity<String> response = restTemplate.getForEntity(mockWaitAPICallURL, String.class);
        System.out.println("Thread: " + Thread.currentThread().getName());
        return response;
    }

    public ResponseEntity<String> fallbackForBulkheadSemaphore(Throwable throwable) {
        return new ResponseEntity<>("Semaphore::Too many requests", HttpStatus.TOO_MANY_REQUESTS);
    }

    public String mockWaitAPICall() {
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return "Success!";
    }

    @Bulkhead(name = "testBulkheadThreadPool", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "fallbackForBulkheadThreadPool")
    public CompletableFuture<ResponseEntity<String>> bulkheadThreadPool() {
        System.out.println("Bulkhead thread pool");
        return CompletableFuture.supplyAsync(() -> {
            ResponseEntity<String> response = restTemplate.getForEntity(mockWaitAPICallURL, String.class);
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
    public ResponseEntity<String> doRateLimit() {
        System.out.println("RateLimit API Call");
        return restTemplate.getForEntity(rateLimitTestApiURL, String.class);
    }

    public ResponseEntity<String> fallbackForRateLimiter(Throwable throwable) {
        return new ResponseEntity<>("Too Many Requests", HttpStatus.TOO_MANY_REQUESTS);
    }

    @TimeLimiter(name = "testTimeLimiter", fallbackMethod = "fallbackForTimeLimiter")
    public CompletableFuture<ResponseEntity<String>> processAsyncTask() {
        return CompletableFuture.supplyAsync(() -> {
            ResponseEntity<String> response = restTemplate.getForEntity(mockWaitAPICallURL, String.class);
            System.out.println("response: " + response.getBody());
            return response;
        });
    }

    public CompletableFuture<ResponseEntity<String>> fallbackForTimeLimiter(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                            .body("Timeout occurred: The task took too long to complete."));
        }
        return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("An unexpected error occurred."));
    }
}