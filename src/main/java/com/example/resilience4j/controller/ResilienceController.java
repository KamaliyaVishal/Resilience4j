package com.example.resilience4j.controller;

import com.example.resilience4j.service.ResilienceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class ResilienceController {

    @Autowired
    private ResilienceService resilienceService;

    @GetMapping("/circuitBreaker")
    public ResponseEntity<String> executeRemoteCall() {
        return ResponseEntity.ok(resilienceService.executeRemoteCall());
    }

    @GetMapping("/retry")
    public ResponseEntity<String> doRetryRemoteCall() {
        return ResponseEntity.ok(resilienceService.doRetryRemoteCall());
    }

    @GetMapping("/bulkheadSemaphore")
    public ResponseEntity<String> bulkheadSemaphore() {
        return resilienceService.bulkheadSemaphore();
    }

    @GetMapping("/bulkheadThreadPool")
    public CompletableFuture<ResponseEntity<String>> bulkheadThreadPool() {
        return resilienceService.bulkheadThreadPool();
    }

    @GetMapping("/rateLimiter")
    public ResponseEntity<String> doRateLimit() {
        return resilienceService.doRateLimit();
    }

    @GetMapping("/timeLimiter")
    public CompletableFuture<ResponseEntity<String>> processAsyncTask() {
        return resilienceService.processAsyncTask();
    }

    // Helper endpoints

    @GetMapping("/mockWaitAPICall")
    public ResponseEntity<String> mockWaitAPICall() {
        return ResponseEntity.ok(resilienceService.mockWaitAPICall());
    }

    @GetMapping("/mockRemoteAPICall")
    public ResponseEntity<String> mockRemoteAPICall() {
        return ResponseEntity.ok("Success");
    }
}