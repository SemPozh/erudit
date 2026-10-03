package com.erudit.events;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class EventTypeCatalog {
    private static final Map<String, EventType> BY_VALUE = Arrays.stream(EventType.values())
            .collect(Collectors.toUnmodifiableMap(EventType::value, Function.identity()));
    private static final Map<EventGroup, List<EventType>> BY_GROUP = groupTypes();

    private EventTypeCatalog() {
    }

    public static EventType require(String value) {
        EventType type = BY_VALUE.get(value);
        if (type == null) {
            throw new IllegalArgumentException("Unknown eventType: " + value);
        }
        return type;
    }

    public static List<EventType> types(EventGroup group) {
        return BY_GROUP.getOrDefault(group, List.of());
    }

    private static Map<EventGroup, List<EventType>> groupTypes() {
        Map<EventGroup, List<EventType>> result = new EnumMap<>(EventGroup.class);
        for (EventGroup group : EventGroup.values()) {
            result.put(group, Arrays.stream(EventType.values()).filter(type -> type.group() == group).toList());
        }
        return Collections.unmodifiableMap(result);
    }
}
