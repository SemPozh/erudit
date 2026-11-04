package com.erudit.user.model;
import java.time.OffsetDateTime;
import java.util.UUID;
public record User(UUID id,String email,String displayName,boolean verified,OffsetDateTime createdAt) {}
