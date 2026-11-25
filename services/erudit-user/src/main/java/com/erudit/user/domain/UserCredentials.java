package com.erudit.user.domain;

public record UserCredentials(User user, String passwordHash) { }
