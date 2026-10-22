package com.erudit.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_push_tokens", uniqueConstraints = @UniqueConstraint(columnNames = "token"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevicePushToken {
    @Id
    @NotNull
    private UUID id;

    @Column(name = "user_id", nullable = false)
    @NotNull
    private UUID userId;

    @NotBlank
    @Size(max = 4096)
    @Column(nullable = false, length = 4096)
    private String token;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushPlatform platform;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    @NotNull
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @NotNull
    private Instant updatedAt;
}
