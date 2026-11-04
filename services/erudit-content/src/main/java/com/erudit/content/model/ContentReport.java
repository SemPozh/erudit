package com.erudit.content.model;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
@Table("content_reports")
public record ContentReport(@Id UUID id, UUID contentId, String reason, String status, OffsetDateTime createdAt) {}
