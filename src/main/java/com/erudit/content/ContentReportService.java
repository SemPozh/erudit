package com.erudit.content;

import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import com.erudit.web.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ContentReportService {
    private final ContentRepository contentRepository;
    private final ContentReportRepository reportRepository;
    private final ContentModerationAuditRepository auditRepository;
    private final Clock clock;

    public ContentReportService(ContentRepository contentRepository, ContentReportRepository reportRepository,
                                ContentModerationAuditRepository auditRepository, Clock clock) {
        this.contentRepository = contentRepository;
        this.reportRepository = reportRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    public ContentReport report(UUID contentId, String reason) {
        if (contentRepository.findById(contentId).isEmpty()) {
            throw new NotFoundException("Content not found");
        }
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Report reason is required");
        }
        ContentReport report = new ContentReport(UUID.randomUUID(), contentId, reason, clock.instant());
        reportRepository.save(report);
        return report;
    }

    @Transactional(readOnly = true)
    public ContentReportPage openReports(Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? 20 : requestedSize;
        if (page < 0 || size < 1 || size > 50) {
            throw new ValidationException("page must be non-negative and size must be between 1 and 50");
        }
        return new ContentReportPage(reportRepository.findOpen(page, size), reportRepository.countOpen(), page, size);
    }

    @Transactional
    public void resolve(UUID reportId, ReportDecision decision, String comment, String adminId) {
        ContentReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Content report not found"));
        Instant now = clock.instant();
        if (!reportRepository.resolve(reportId, decision, normalizeComment(comment), now, adminId)) {
            throw new ConflictException("Content report has already been resolved");
        }
        if (decision == ReportDecision.ACCEPT) {
            contentRepository.archiveIfPublished(report.contentId());
        }
        auditRepository.recordReportResolution(reportId, adminId, decision, now);
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) return null;
        String normalized = comment.trim();
        if (normalized.length() > 2000) throw new ValidationException("comment cannot exceed 2000 characters");
        return normalized;
    }
}
