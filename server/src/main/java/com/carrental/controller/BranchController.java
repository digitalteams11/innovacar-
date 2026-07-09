package com.carrental.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

    @RequestMapping
    public ResponseEntity<Map<String, Object>> all() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of("success", false, "code", "FEATURE_REMOVED",
                        "message", "Branches have been removed from this version."));
    }
}
