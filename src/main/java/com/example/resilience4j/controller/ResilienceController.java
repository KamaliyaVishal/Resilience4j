package com.example.resilience4j.controller;

import com.example.resilience4j.service.ResilienceService;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@RestController
public class ResilienceController {

    @Autowired
    private ResilienceService resilienceService;

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping("/test")
    public ResponseEntity<String> doResilientCall() {
        String result = resilienceService.executeRemoteCall();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/retry")
    public ResponseEntity<String> doRetryRemoteCall() {
        String result = resilienceService.doRetryRemoteCall();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/bulkheadSemaphore")
    public ResponseEntity<String> bulkheadSemaphore() {
        String result = resilienceService.bulkheadSemaphore();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/bulkheadThreadPool")
    public ResponseEntity<String> bulkheadThreadPool() {
        CompletableFuture<ResponseEntity<String>> result = resilienceService.bulkheadThreadPool();
        return ResponseEntity.ok(result.toString());
    }

    @GetMapping("/doBulkheadCalls")
    public ResponseEntity<String> doBulkheadCalls() {
        String result = resilienceService.doBulkheadCalls();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rateLimiter")
    public ResponseEntity<String> doRateLimit() {
        String result = resilienceService.doRateLimit();
        return ResponseEntity.ok(result);
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
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok("Success");
    }


    @GetMapping("/timeLimiter")
    @TimeLimiter(name = "timeLimiter", fallbackMethod = "fallbackForTimeLimiter")
    public CompletableFuture<ResponseEntity<String>> processAsyncTask() {
        return CompletableFuture.supplyAsync(() -> {
            String dummyApiUrl = "http://localhost:8090/takingTimeAPI";
            ResponseEntity<String> response = restTemplate.getForEntity(dummyApiUrl, String.class);
            System.out.println("response: " + response.getBody());
            return ResponseEntity.ok(response.getBody());
        });
    }

    public CompletableFuture<ResponseEntity<String>> fallbackForTimeLimiter(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                            .body("Timeout occurred: The task took too long to complete.")
            );
        }
        return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("An unexpected error occurred.")
        );
    }

}
