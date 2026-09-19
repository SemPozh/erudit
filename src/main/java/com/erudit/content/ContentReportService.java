package com.erudit.content;

import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ContentReportService {
    private final ContentRepository contentRepository;
    private final ContentReportRepository reportRepository;

    public ContentReportService(ContentRepository contentRepository, ContentReportRepository reportRepository) {
        this.contentRepository = contentRepository;
        this.reportRepository = reportRepository;
    }

    public ContentReport report(UUID contentId, String reason) {
        if (contentRepository.findById(contentId).isEmpty()) {
            throw new NotFoundException("Content not found");
        }
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Report reason is required");
        }
        ContentReport report = new ContentReport(UUID.randomUUID(), contentId, reason, Instant.now());
        reportRepository.save(report);
        return report;
    }
}
