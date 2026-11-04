package com.erudit.user.model;
import java.util.UUID;
public record UserSettings(UUID userId,String locale,int dailyGoal) {}
