package com.erudit.user.domain;
import java.util.UUID;
public record UserSettings(UUID userId,String locale,int dailyGoal) {}
