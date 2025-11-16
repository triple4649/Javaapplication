package com.example.demo_security.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/public")
    public String publicApi() {
        return "Public OK";
    }

    @GetMapping("/secure")
    public String secureApi() {
        return "Secure OK (auth required)";
    }
}
