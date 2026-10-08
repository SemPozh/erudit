package com.erudit.events.model;

public enum EventType {
    SCREEN_OPENED("screen_opened", EventGroup.NAVIGATION),
    BUTTON_CLICKED("button_clicked", EventGroup.NAVIGATION),
    LINK_CLICKED("link_clicked", EventGroup.NAVIGATION),

    CONTENT_VIEWED("content_viewed", EventGroup.CONTENT),
    CONTENT_STARTED("content_started", EventGroup.CONTENT),
    CONTENT_COMPLETED("content_completed", EventGroup.CONTENT),

    QUIZ_STARTED("quiz_started", EventGroup.QUIZ),
    QUIZ_ANSWERED("quiz_answered", EventGroup.QUIZ),
    QUIZ_COMPLETED("quiz_completed", EventGroup.QUIZ),
    COMPETITION_COMPLETED("competition_completed", EventGroup.QUIZ),

    FRIEND_REQUESTED("friend_requested", EventGroup.SOCIAL),
    FRIEND_ADDED("friend_added", EventGroup.SOCIAL),
    FRIEND_OVERTAKEN("friend_overtaken", EventGroup.SOCIAL),

    SUBSCRIPTION_STARTED("subscription_started", EventGroup.MONETIZATION),
    PAYMENT_SUCCEEDED("payment_succeeded", EventGroup.MONETIZATION),
    SUBSCRIPTION_CANCELLED("subscription_cancelled", EventGroup.MONETIZATION),
    SUBSCRIPTION_EXPIRED("subscription_expired", EventGroup.MONETIZATION),

    NOTIFICATION_DELIVERED("notification_delivered", EventGroup.NOTIFICATION),
    NOTIFICATION_OPENED("notification_opened", EventGroup.NOTIFICATION),
    NOTIFICATION_CLICKED("notification_clicked", EventGroup.NOTIFICATION),
    NOTIFICATION_UNSUBSCRIBED("notification_unsubscribed", EventGroup.NOTIFICATION);

    private final String value;
    private final EventGroup group;

    EventType(String value, EventGroup group) {
        this.value = value;
        this.group = group;
    }

    public String value() {
        return value;
    }

    public EventGroup group() {
        return group;
    }
}
