package com.erudit.user.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String language = "ru";

    @Column(name = "time_zone", nullable = false, length = 50)
    @Builder.Default
    private String timeZone = "UTC";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_favorite_categories", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "category_id", nullable = false)
    @Builder.Default
    private Set<UUID> favoriteCategories = new LinkedHashSet<>();

    @Column(name = "daily_goal_minutes", nullable = false)
    @Builder.Default
    private int dailyGoalMinutes = 15;

    @Column(name = "visible_in_search", nullable = false)
    @Builder.Default
    private boolean visibleInSearch = true;

    @Column(name = "visible_in_rating", nullable = false)
    @Builder.Default
    private boolean visibleInRating = true;

    @Column(name = "analytics_consent", nullable = false)
    @Builder.Default
    private boolean analyticsConsent = false;
}
