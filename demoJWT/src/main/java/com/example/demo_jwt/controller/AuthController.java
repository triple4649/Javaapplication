package com.example.demo_jwt.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo_jwt.service.JwtService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public record LoginRequest(String username, String password) {}

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest req) {

        // ★ 本来は DBなどでユーザ検証する
        if (!req.username().equals("admin") || !req.password().equals("pass123")) {
            throw new RuntimeException("Invalid credentials");
        }

        return jwtService.generateToken(req.username());
    }
}
