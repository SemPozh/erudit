package com.erudit.user.service;

public interface PasswordHasher {
    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
