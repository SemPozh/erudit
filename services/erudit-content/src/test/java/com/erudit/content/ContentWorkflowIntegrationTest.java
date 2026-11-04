package com.erudit.content;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.erudit.content.model.ContentItem;
import com.erudit.content.service.*;
import com.erudit.content.repository.ContentWorkflowRepository;
import com.erudit.integration.InternalClients.EntitlementClient;
import com.erudit.integration.InternalContracts.Entitlement;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @AutoConfigureMockMvc
@SpringBootTest(properties={"spring.cloud.config.enabled=false","eureka.client.enabled=false","spring.config.name=isolated-test","media.storage-path=build/workflow-media"})
class ContentWorkflowIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    static final UUID USER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID CATEGORY=UUID.fromString("10000000-0000-0000-0000-000000000001");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ContentItemService items;
    @Autowired ContentWorkflowService workflows;
    @Autowired ContentWorkflowRepository repository;
    @MockitoBean EntitlementClient entitlements;
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE content CASCADE");
        when(entitlements.get(any())).thenAnswer(call -> new Entitlement(call.getArgument(0),false,null));
    }
    private ContentItem create(boolean premium) {
        return items.create(new ContentItemService.Draft(CATEGORY,"ARTICLE","Workflow "+UUID.randomUUID(),
                "Source description","First source fact. Second source fact.",null,"BEGINNER",5,premium,List.of("workflow")));
    }
    private ContentItem published(boolean premium) { var item=create(premium);return workflows.moderate(item.id(),"PUBLISHED",USER,null); }
    private ContentItemService.Draft draft(ContentItem c,String body) {
        return new ContentItemService.Draft(c.categoryId(),c.type(),c.title(),c.description(),body,c.mediaUrl(),c.difficulty(),c.estimatedMinutes(),c.premiumLocked(),c.tags());
    }
    @Test void moderationLifecycleAndPublicVisibilityMatchEveryContentStatus() throws Exception {
        var item=create(false);
        assertThat(item.status()).isEqualTo("PENDING_MODERATION");
        mvc.perform(get("/api/v1/content/{id}",item.id())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/content/{id}/view",item.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/content/moderation")).andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count","1")).andExpect(jsonPath("$.pagination.totalElements").value(1));
        mvc.perform(post("/api/v1/admin/content/{id}/approve",item.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mvc.perform(post("/api/v1/admin/content/{id}/reject",item.id())).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/content/{id}",item.id())).andExpect(status().isOk());
        assertThat(items.update(item.id(),draft(item,"New source fact.")).status()).isEqualTo("PENDING_MODERATION");
        mvc.perform(post("/api/v1/admin/content/{id}/reject",item.id())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/content/{id}",item.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/content/{id}/quiz-cards",item.id())).andExpect(status().isNotFound());
        items.archive(item.id());
        assertThat(items.get(item.id()).status()).isEqualTo("ARCHIVED");
        mvc.perform(post("/api/v1/content/{id}/complete",item.id())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content_moderation_audit",Integer.class)).isEqualTo(2);
    }
    @Test void progressCompletionIsIdempotentAndIsolatedByUser() throws Exception {
        var item=published(false);
        mvc.perform(get("/api/v1/content/{id}/progress",item.id())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/content/{id}/view",item.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VIEWED"));
        var viewed=repository.progress(USER,item.id()).orElseThrow();
        mvc.perform(post("/api/v1/content/{id}/complete",item.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        var completed=repository.progress(USER,item.id()).orElseThrow();
        workflows.record(USER,item.id(),false);workflows.record(USER,item.id(),true);
        assertThat(repository.progress(USER,item.id()).orElseThrow()).isEqualTo(completed);
        assertThat(completed.viewedAt()).isEqualTo(viewed.viewedAt());
        mvc.perform(get("/api/v1/content/{id}/progress",item.id())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/content/{id}/progress",item.id()).header("X-User-Id",UUID.randomUUID())).andExpect(status().isNotFound());
    }
    @Test void concurrentViewAndCompleteNeverDowngradeOrDuplicateProgress() throws Exception {
        var item=published(false);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var view=pool.submit(() -> {start.await();return workflows.record(USER,item.id(),false);});
            var complete=pool.submit(() -> {start.await();return workflows.record(USER,item.id(),true);});
            start.countDown();view.get(15,TimeUnit.SECONDS);complete.get(15,TimeUnit.SECONDS);
        }
        var progress=repository.progress(USER,item.id()).orElseThrow();
        assertThat(progress.status()).isEqualTo("COMPLETED");
        assertThat(progress.completedAt()).isAfterOrEqualTo(progress.viewedAt());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content_progress",Integer.class)).isOne();
    }
    @Test void quizCardsArePersistedOnceAndRegeneratedAtomicallyWithAnEdit() throws Exception {
        var item=published(false);
        var first=workflows.cards(USER,item.id());
        assertThat(first).hasSize(2);
        assertThat(workflows.cards(USER,item.id())).isEqualTo(first);
        mvc.perform(get("/api/v1/content/{id}/quiz-cards",item.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fact").value("First source fact."));
        items.update(item.id(),draft(item,"Replacement source fact."));
        workflows.moderate(item.id(),"PUBLISHED",USER,null);
        var regenerated=workflows.cards(USER,item.id());
        assertThat(regenerated).hasSize(1);assertThat(regenerated.getFirst().fact()).isEqualTo("Replacement source fact.");
        assertThat(regenerated.getFirst().id()).isNotEqualTo(first.getFirst().id());
        jdbc.update("DELETE FROM content_quiz_cards WHERE content_id=?",item.id());
        assertThat(workflows.cards(USER,item.id())).hasSize(1); // Backfill content that predates extraction.
    }
    @Test void premiumGateFailsClosedForProgressAndCardsButKeepsFeedTeasers() throws Exception {
        var item=published(true);
        for(String suffix:List.of("view","complete")) mvc.perform(post("/api/v1/content/{id}/"+suffix,item.id())).andExpect(status().isForbidden());
        for(String suffix:List.of("progress","quiz-cards")) mvc.perform(get("/api/v1/content/{id}/"+suffix,item.id())).andExpect(status().isForbidden());
        when(entitlements.get(USER)).thenThrow(new IllegalStateException("payment offline"));
        mvc.perform(post("/api/v1/content/{id}/view",item.id())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/content/feed")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].body").value("Premium content"))
                .andExpect(header().doesNotExist("X-Total-Count")).andExpect(jsonPath("$.pagination").doesNotExist());
        assertThat(repository.progress(USER,item.id())).isEmpty();
        doReturn(new Entitlement(USER,true,null)).when(entitlements).get(USER);
        mvc.perform(post("/api/v1/content/{id}/complete",item.id())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/content/{id}/quiz-cards",item.id())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/content/feed")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].body").value(item.body()));
    }
    @Test void feedUsesBehaviorAndHasMoreWithoutCountingHiddenOrOtherUsersProgress() throws Exception {
        published(false);published(false);var newest=published(false);create(false);
        var rejected=create(false);workflows.moderate(rejected.id(),"REJECTED",USER,null);
        var archived=published(false);items.archive(archived.id());
        workflows.record(USER,newest.id(),true);
        var first=workflows.feed(USER,0,2);assertThat(first.hasMore()).isTrue();
        assertThat(first.items()).extracting(ContentItem::id).doesNotContain(newest.id(),rejected.id(),archived.id());
        assertThat(workflows.feed(UUID.randomUUID(),0,1).items().getFirst().id()).isEqualTo(newest.id());
        mvc.perform(get("/api/v1/content/feed").param("size","2")).andExpect(status().isOk())
                .andExpect(header().string("X-Has-More","true")).andExpect(header().string("X-Next-Page","1"));
        mvc.perform(get("/api/v1/content/feed").param("size","2").param("page","1")).andExpect(status().isOk())
                .andExpect(header().string("X-Has-More","false")).andExpect(header().doesNotExist("X-Next-Page"))
                .andExpect(header().doesNotExist("X-Total-Count")).andExpect(jsonPath("$.data[0].id").value(newest.id().toString()));
    }
    @Test void reportResolutionArchivesOnceAndPreservesUnverifiedActorAudit() throws Exception {
        var item=published(false);var report=workflows.report(item.id(),"  Factual error in this material  ");
        UUID actor=UUID.randomUUID();
        mvc.perform(get("/api/v1/admin/content/reports")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count","1"));
        mvc.perform(post("/api/v1/admin/content/reports/{id}/resolve",report.id()).header("X-User-Id",actor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\",\"comment\":\" Checked \"}"))
                .andExpect(status().isNoContent());
        assertThat(items.get(item.id()).status()).isEqualTo("ARCHIVED");
        assertThat(repository.report(report.id()).orElseThrow().status()).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("SELECT resolved_by FROM content_reports WHERE id=?",UUID.class,report.id())).isEqualTo(actor);
        mvc.perform(post("/api/v1/admin/content/reports/{id}/resolve",report.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}")).andExpect(status().isConflict());
        assertThat(workflows.reports(0,20).total()).isZero();
    }
    @Test void rejectedReportLeavesPublishedContentAndInternalApisWork() throws Exception {
        var pending=create(false);
        String body="{\"contentId\":\""+pending.id()+"\",\"status\":\"PUBLISHED\",\"reason\":\"Reviewed\"}";
        mvc.perform(get("/internal/v1/content/moderation")).andExpect(status().isOk());
        mvc.perform(post("/internal/v1/moderation").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentId").value(pending.id().toString()));
        mvc.perform(post("/api/v1/content/{id}/report",pending.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Factual error in this material\"}")).andExpect(status().isCreated());
        var report=workflows.reports(0,20).items().getFirst();
        mvc.perform(get("/internal/v1/content/reports")).andExpect(status().isOk());
        mvc.perform(post("/internal/v1/content/reports/{id}/resolve",report.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}")).andExpect(status().isNoContent());
        assertThat(items.get(pending.id()).status()).isEqualTo("PUBLISHED");
    }
    private List<Boolean> race(Callable<Boolean> a,Callable<Boolean> b) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> first=() -> {start.await();return a.call();};Callable<Boolean> second=() -> {start.await();return b.call();};
            var x=pool.submit(first);var y=pool.submit(second);start.countDown();return List.of(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS));
        }
    }
    private boolean decision(Runnable action) {try {action.run();return true;}catch(CategoryConflictException conflict){return false;}}
    @Test void concurrentModerationAndResolutionsProduceOnlyOneAuditEach() throws Exception {
        var item=create(false);
        assertThat(race(() -> decision(() -> workflows.moderate(item.id(),"PUBLISHED",USER,null)),
                () -> decision(() -> workflows.moderate(item.id(),"REJECTED",USER,null)))).containsExactlyInAnyOrder(true,false);
        if (!items.get(item.id()).status().equals("PUBLISHED")) {items.update(item.id(),draft(item,item.body()));workflows.moderate(item.id(),"PUBLISHED",USER,null);}
        var report=workflows.report(item.id(),"Reported factual error here");
        assertThat(race(() -> decision(() -> workflows.resolve(report.id(),"ACCEPT",null,USER)),
                () -> decision(() -> workflows.resolve(report.id(),"REJECT",null,USER)))).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content_moderation_audit WHERE report_id=?",Integer.class,report.id())).isOne();
    }
    @Test void failedAuditRollsBackModerationReportResolutionAndArchive() {
        var item=published(false);var report=workflows.report(item.id(),"Reported factual error here");var pending=create(false);
        jdbc.execute("ALTER TABLE content_moderation_audit ADD CONSTRAINT test_reject CHECK(action='NEVER') NOT VALID");
        try {
            assertThatThrownBy(() -> workflows.resolve(report.id(),"ACCEPT",null,USER)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(items.get(item.id()).status()).isEqualTo("PUBLISHED");assertThat(repository.report(report.id()).orElseThrow().status()).isEqualTo("OPEN");
            assertThatThrownBy(() -> workflows.moderate(pending.id(),"PUBLISHED",USER,null)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(items.get(pending.id()).status()).isEqualTo("PENDING_MODERATION");
        } finally {jdbc.execute("ALTER TABLE content_moderation_audit DROP CONSTRAINT test_reject");}
    }
    @Test void validationMissingResourcesAndPageLimitsDoNotChangeData() throws Exception {
        var item=published(false);
        mvc.perform(post("/api/v1/content/{id}/report",item.id()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"short\"}"))
                .andExpect(status().isBadRequest());
        for(String path:List.of("/api/v1/content/feed","/api/v1/admin/content/moderation","/api/v1/admin/content/reports")) {
            mvc.perform(get(path).param("size","51")).andExpect(status().isBadRequest());
            mvc.perform(get(path).param("page","-1")).andExpect(status().isBadRequest());
        }
        assertThatThrownBy(() -> workflows.report(item.id()," ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workflows.moderate(item.id(),"ARCHIVED",USER,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workflows.resolve(UUID.randomUUID(),"ACCEPT",null,USER)).isInstanceOf(CategoryNotFoundException.class);
        var report=workflows.report(item.id(),"Reported factual error here");
        assertThatThrownBy(() -> workflows.resolve(report.id(),"ACCEPT","x".repeat(2001),USER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.report(report.id()).orElseThrow().status()).isEqualTo("OPEN");
        assertThatThrownBy(() -> workflows.feed(USER,0,0)).isInstanceOf(IllegalArgumentException.class);
    }
}
