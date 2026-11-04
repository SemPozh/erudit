package com.erudit.content.service;

import com.erudit.content.model.ContentItem;
import com.erudit.content.model.ContentProgress;
import com.erudit.content.model.ContentReport;
import com.erudit.content.model.QuizCard;
import com.erudit.content.repository.ContentItemRepository;
import com.erudit.content.repository.ContentWorkflowRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentWorkflowService {
    private final ContentItemService items;
    private final ContentItemRepository content;
    private final ContentWorkflowRepository workflows;
    private final PremiumAccessService premium;
    private final QuizCardGenerator generator;

    @Transactional(readOnly=true)
    public ContentProgress progress(UUID user,UUID id) {
        requireAccess(user,id);
        return workflows.progress(user,id).orElseThrow(() -> new CategoryNotFoundException(id));
    }
    @Transactional
    public ContentProgress record(UUID user,UUID id,boolean complete) {
        // Sharing the content row lock with edits, archive and moderation prevents writing progress for hidden content.
        content.lock(id);
        requireAccess(user,id);
        return workflows.record(user,id,complete);
    }
    @Transactional
    public List<QuizCard> cards(UUID user,UUID id) {
        content.lock(id);
        var item=requireAccess(user,id);
        var cards=workflows.cards(id);
        if (cards.isEmpty()) { cards=generator.generate(item); workflows.replaceCards(id,cards); }
        return cards;
    }
    @Transactional(readOnly=true)
    public Feed feed(UUID user,int page,int size) {
        validatePage(page,size);
        var rows=content.feed(user,page,size);
        return new Feed(rows.size()>size ? List.copyOf(rows.subList(0,size)) : rows,rows.size()>size,page);
    }
    @Transactional(readOnly=true)
    public ContentPage queue(int page,int size) {
        validatePage(page,size);
        return new ContentPage(content.pending(page,size),workflows.countPending(),page,size);
    }
    @Transactional
    public ContentItem moderate(UUID id,String status,UUID actor,String reason) {
        if (!Set.of("PUBLISHED","REJECTED").contains(status)) throw new IllegalArgumentException("Invalid moderation decision");
        String comment=comment(reason);
        content.lock(id);
        if (!workflows.decide(id,status)) throw new CategoryConflictException("Content has already been moderated");
        workflows.audit(id,null,actor,"MODERATE_"+status,comment);
        return items.get(id);
    }
    @Transactional
    public ContentReport report(UUID id,String reason) {
        if (reason==null || reason.trim().length()<10 || reason.trim().length()>500)
            throw new IllegalArgumentException("Report reason must contain 10 to 500 characters");
        content.lock(id);
        requirePublished(id);
        return workflows.report(id,reason.trim());
    }
    @Transactional(readOnly=true)
    public ReportPage reports(int page,int size) {
        validatePage(page,size);
        return new ReportPage(workflows.reports(page,size),workflows.countReports(),page,size);
    }
    @Transactional
    public void resolve(UUID id,String decision,String comment,UUID actor) {
        if (!Set.of("ACCEPT","REJECT").contains(decision)) throw new IllegalArgumentException("Invalid report decision");
        String normalized=comment(comment);
        var report=workflows.report(id).orElseThrow(() -> new CategoryNotFoundException(id));
        // One transaction covers the conditional resolution, optional archive and audit entry.
        // Lock content first for all moderation paths, so concurrent resolutions use the same lock order.
        content.lock(report.contentId());
        if (!workflows.resolve(id,decision,normalized,actor)) throw new CategoryConflictException("Report has already been resolved");
        if (decision.equals("ACCEPT")) workflows.archivePublished(report.contentId());
        workflows.audit(report.contentId(),id,actor,"REPORT_"+decision,normalized);
    }
    private ContentItem requirePublished(UUID id) {
        var item=items.get(id);
        if (!item.status().equals("PUBLISHED")) throw new CategoryNotFoundException(id);
        return item;
    }
    private ContentItem requireAccess(UUID user,UUID id) {
        var item=requirePublished(id);
        if (item.premiumLocked() && !premium.premiumFor(user.toString())) throw new ContentAccessException();
        return item;
    }
    private static String comment(String value) {
        if (value==null || value.isBlank()) return null;
        if (value.trim().length()>2000) throw new IllegalArgumentException("Comment cannot exceed 2000 characters");
        return value.trim();
    }
    private static void validatePage(int page,int size) {
        if (page<0 || size<1 || size>50) throw new IllegalArgumentException("Invalid page or size");
    }
    public record Feed(List<ContentItem> items,boolean hasMore,int page) {}
    public record ContentPage(List<ContentItem> items,long total,int page,int size) {}
    public record ReportPage(List<ContentReport> items,long total,int page,int size) {}
}
