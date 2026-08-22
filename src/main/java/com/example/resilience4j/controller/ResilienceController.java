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

}
