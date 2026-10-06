package com.erudit.user.service;

public record IssuedTokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
