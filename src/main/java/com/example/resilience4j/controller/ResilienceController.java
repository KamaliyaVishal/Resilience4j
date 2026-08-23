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

    @GetMapping("/test")
    public ResponseEntity<String> doResilientCall() {
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

    @GetMapping("/doBulkheadCalls")
    public ResponseEntity<String> doBulkheadCalls() {
        return ResponseEntity.ok(resilienceService.doBulkheadCalls());
    }

    @GetMapping("/rateLimiter")
    public ResponseEntity<String> doRateLimit() {
        return resilienceService.doRateLimit();
    }

    @GetMapping("/testRateLimit")
    public ResponseEntity<String> testRateLimit() {
        return ResponseEntity.ok("Success");
    }

    @GetMapping("/takingTimeAPI")
    public ResponseEntity<String> takingTimeAPI() {
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok("Success");
    }

    @GetMapping("/timeLimiter")
    public CompletableFuture<ResponseEntity<String>> processAsyncTask() {
        return resilienceService.processAsyncTask();
    }
}