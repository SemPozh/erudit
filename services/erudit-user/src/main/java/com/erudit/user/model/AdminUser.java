package com.erudit.user.model;
import java.time.OffsetDateTime;
import java.util.UUID;
public record AdminUser(UUID id,String email,String name,OffsetDateTime registeredAt,String status){}
