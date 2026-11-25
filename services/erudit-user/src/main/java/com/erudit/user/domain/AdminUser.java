package com.erudit.user.domain;
import java.time.OffsetDateTime;
import java.util.UUID;
public record AdminUser(UUID id,String email,String name,OffsetDateTime registeredAt,String status){}
