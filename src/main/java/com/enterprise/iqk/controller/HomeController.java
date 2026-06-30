package com.enterprise.iqk.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "name", "LexScope Agent",
                "status", "running",
                "frontend", "http://localhost:8088/",
                "swagger", "http://localhost:8080/swagger-ui/index.html",
                "health", "http://localhost:8080/actuator/health"
        );
    }
}
