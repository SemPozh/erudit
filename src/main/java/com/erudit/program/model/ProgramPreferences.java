package com.erudit.program.model;

import java.util.Map;

public record ProgramPreferences(Map<String, Integer> topicWeights) {
    public int weight(String topic) {
        return topicWeights.getOrDefault(topic, 0);
    }
}

