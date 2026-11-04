package com.erudit.content.controller;

import com.erudit.content.model.ContentProgress;
import com.erudit.content.openapi.api.ContentWorkflowsApi;
import com.erudit.content.openapi.model.*;
import com.erudit.content.service.ContentWorkflowService;
import com.erudit.content.service.PremiumAccessService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ContentWorkflowController implements ContentWorkflowsApi {
    private final ContentWorkflowService service;
    private final PremiumAccessService premium;
    private UUID user(UUID id) { return id==null ? UUID.fromString("00000000-0000-0000-0000-000000000001") : id; }
    @Override public ResponseEntity<ContentProgressResponse> getContentProgress(UUID id,UUID xUserId) {
        return ResponseEntity.ok(progress(service.progress(user(xUserId),id)));
    }
    @Override public ResponseEntity<ContentProgressResponse> markContentViewed(UUID id,UUID xUserId) {
        return ResponseEntity.ok(progress(service.record(user(xUserId),id,false)));
    }
    @Override public ResponseEntity<ContentProgressResponse> completeContent(UUID id,UUID xUserId) {
        return ResponseEntity.ok(progress(service.record(user(xUserId),id,true)));
    }
    @Override public ResponseEntity<QuizCardListResponse> getContentQuizCards(UUID id,UUID xUserId) {
        return ResponseEntity.ok(new QuizCardListResponse(service.cards(user(xUserId),id).stream()
                .map(c -> new QuizCardDataResponse(c.id(),c.fact(),c.question(),c.answers())).toList()));
    }
    @Override public ResponseEntity<ContentFeedResponse> getContentFeed(Integer page,Integer size,UUID xUserId) {
        var result=service.feed(user(xUserId),page,size);
        boolean access=result.items().stream().anyMatch(com.erudit.content.model.ContentItem::premiumLocked) && premium.premiumFor(user(xUserId).toString());
        var response=ResponseEntity.ok().header("X-Has-More",Boolean.toString(result.hasMore()));
        if (result.hasMore()) response.header("X-Next-Page",Integer.toString(page+1));
        return response.body(new ContentFeedResponse(result.items().stream()
                .map(item -> ContentItemController.data(ContentItemController.visible(item,access))).toList()));
    }
    @Override public ResponseEntity<WorkflowContentPageResponse> listModerationQueue(Integer page,Integer size,UUID xUserId) {
        var result=service.queue(page,size);
        return ResponseEntity.ok().header("X-Total-Count",Long.toString(result.total())).body(new WorkflowContentPageResponse(
                result.items().stream().map(ContentItemController::data).toList(),metadata(page,size,result.total())));
    }
    @Override public ResponseEntity<ContentResponse> approveContent(UUID id,UUID xUserId) {
        return ResponseEntity.ok(ContentItemController.response(service.moderate(id,"PUBLISHED",user(xUserId),null)));
    }
    @Override public ResponseEntity<ContentResponse> rejectContent(UUID id,UUID xUserId) {
        return ResponseEntity.ok(ContentItemController.response(service.moderate(id,"REJECTED",user(xUserId),null)));
    }
    @Override public ResponseEntity<ReportCreatedResponse> reportContent(UUID id,ContentReportRequest request,UUID xUserId) {
        return ResponseEntity.status(201).body(new ReportCreatedResponse(service.report(id,request.getReason()).id()));
    }
    @Override public ResponseEntity<ContentReportListResponse> listContentReports(Integer page,Integer size,UUID xUserId) {
        var result=service.reports(page,size);
        return ResponseEntity.ok().header("X-Total-Count",Long.toString(result.total())).body(new ContentReportListResponse(
                result.items().stream().map(r -> new ContentReportItemDataResponse(r.id(),r.contentId(),r.status())
                        .reason(r.reason()).createdAt(r.createdAt())).toList()).pagination(metadata(page,size,result.total())));
    }
    @Override public ResponseEntity<Void> resolveContentReport(UUID id,ReportResolutionRequest request,UUID xUserId) {
        service.resolve(id,request.getDecision().getValue(),request.getComment(),user(xUserId));
        return ResponseEntity.noContent().build();
    }
    @Override public ResponseEntity<InternalModerationResponse> moderateInternal(InternalModerationRequest request,UUID xUserId) {
        var item=service.moderate(request.getContentId(),request.getStatus().getValue(),user(xUserId),request.getReason());
        return ResponseEntity.ok(new InternalModerationResponse(item.id(),item.status()).reason(request.getReason()));
    }
    @Override public ResponseEntity<WorkflowContentPageResponse> internalListModerationQueue(Integer page,Integer size,UUID xUserId) { return listModerationQueue(page,size,xUserId); }
    @Override public ResponseEntity<ContentReportListResponse> internalListContentReports(Integer page,Integer size,UUID xUserId) { return listContentReports(page,size,xUserId); }
    @Override public ResponseEntity<Void> internalResolveContentReport(UUID id,ReportResolutionRequest request,UUID xUserId) { return resolveContentReport(id,request,xUserId); }
    private ContentProgressResponse progress(ContentProgress p) {
        return new ContentProgressResponse(new ContentProgressDataResponse(p.contentId(),p.status())
                .viewedAt(p.viewedAt()).completedAt(p.completedAt()));
    }
    private WorkflowPageMetadata metadata(int page,int size,long total) {
        return new WorkflowPageMetadata(page,size,total,(int)((total+size-1)/size));
    }
}
