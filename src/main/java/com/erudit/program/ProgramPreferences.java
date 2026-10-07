package com.erudit.program;

import java.util.Map;

public record ProgramPreferences(Map<String, Integer> topicWeights) {
    public int weight(String topic) {
        return topicWeights.getOrDefault(topic, 0);
    }
}

