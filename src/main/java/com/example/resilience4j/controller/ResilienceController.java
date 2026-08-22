package com.example.resilience4j.controller;

import com.example.resilience4j.service.ResilienceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
