package com.erudit.user.model;
import java.util.UUID;
public final class SocialModels {private SocialModels(){} public record Device(UUID id,UUID userId,String token,String platform){} public record PublicUser(UUID id,String displayName){} public record InternalUser(UUID id,String displayName,boolean active,java.util.List<UUID> deviceIds){} }
