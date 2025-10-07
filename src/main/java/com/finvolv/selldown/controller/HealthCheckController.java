package com.finvolv.selldown.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {
    @Autowired
    private ApplicationContext applicationContext;

    @GetMapping("/health-check")
    public String healthCheck() {
        return "Selldown is up and Running";
    }
}
