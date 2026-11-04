package com.erudit.admin;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers @AutoConfigureMockMvc
@SpringBootTest(properties={"spring.config.name=isolated-test","spring.cloud.config.enabled=false","eureka.client.enabled=false",
        "spring.cloud.openfeign.circuitbreaker.enabled=true","spring.cloud.loadbalancer.retry.enabled=false",
        "spring.cloud.openfeign.client.config.default.connectTimeout=200","spring.cloud.openfeign.client.config.default.readTimeout=200"})
class AdminFacadeIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    static final UUID USER=UUID.fromString("00000000-0000-0000-0000-000000000001"),OTHER=UUID.fromString("00000000-0000-0000-0000-000000000002"),ITEM=UUID.randomUUID(),TEMPLATE=UUID.randomUUID();
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final AtomicInteger MODE=new AtomicInteger(),ENQUEUES=new AtomicInteger(),BLOCKS=new AtomicInteger();
    static final AtomicReference<String> LAST_REQUEST_ID=new AtomicReference<>();
    static final HttpServer STUB;
    static final java.util.concurrent.ExecutorService POOL=Executors.newVirtualThreadPerTaskExecutor();
    static {
        try {
            STUB=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);STUB.setExecutor(POOL);
            STUB.createContext("/",x->{
                try {
                    String path=x.getRequestURI().getPath();LAST_REQUEST_ID.set(x.getRequestHeaders().getFirst("X-Request-ID"));
                    String raw=new String(x.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                    int status=200;Object body=Map.of();
                    if(MODE.get()==1){status=503;body=Map.of("error","offline");}
                    else if(path.equals("/internal/v1/admin/users")){
                        if(MODE.get()==3)try{Thread.sleep(600);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                        body=MODE.get()==2?Map.of("unexpected",true):Map.of("data",List.of(Map.of("id",USER,"email","demo@example.com","name","Demo","registeredAt","2026-11-04T10:00:00Z","status","ACTIVE")),"page",0,"size",20,"total",1);
                    }else if(path.endsWith("/block")){BLOCKS.incrementAndGet();status=MODE.get()==4?404:204;}
                    else if(path.startsWith("/internal/v1/users/")){UUID id=UUID.fromString(path.substring(path.lastIndexOf('/')+1));body=Map.of("id",id,"displayName","Demo","active",true);}
                    else if(path.equals("/internal/v1/content/moderation")){body=Map.of("data",List.of(Map.of("id",ITEM,"type","ARTICLE","title","Fixture","status","PENDING_MODERATION")),"pagination",Map.of("page",0,"size",20,"totalElements",1,"totalPages",1));x.getResponseHeaders().add("X-Total-Count","1");}
                    else if(path.equals("/internal/v1/moderation")){var request=JSON.readTree(raw);String state=request.path("status").asText();body=Map.of("contentId",request.path("contentId").asText(),"status",state,"data",Map.of("id",request.path("contentId").asText(),"type","ARTICLE","title","Fixture","status",state));}
                    else if(path.equals("/internal/v1/content/reports")){body=Map.of("data",List.of(),"pagination",Map.of("page",0,"size",20,"totalElements",0,"totalPages",0));x.getResponseHeaders().add("X-Total-Count","0");}
                    else if(path.endsWith("/resolve")){status=204;}
                    else if(path.startsWith("/api/v1/notifications/templates/")){body=Map.of("id",TEMPLATE,"code","DEMO","titleTemplate","Hi {{name}}","bodyTemplate","Body {{name}}","enabled",MODE.get()!=5);}
                    else if(path.equals("/internal/v1/notifications/enqueue")){
                        ENQUEUES.incrementAndGet();UUID id=UUID.fromString(JSON.readTree(raw).path("userId").asText());
                        if(MODE.get()==6&&id.equals(OTHER)){status=503;body=Map.of("error","rejected");}else body=Map.of("id",UUID.randomUUID(),"userId",id);
                    }else if(path.equals("/api/v1/subscriptions/me")){body=Map.of("data",Map.of("id",ITEM,"status","ACTIVE","plan","PREMIUM_MONTHLY"));}
                    else if(path.equals("/api/v1/subscriptions/payments")){body=Map.of("data",List.of(Map.of("id",ITEM,"status","PAID","amount",299)),"pagination",Map.of("page",0,"size",20,"totalElements",1,"totalPages",1));x.getResponseHeaders().add("X-Total-Count","1");}
                    else {status=404;body=Map.of("error","not found");}
                    if(status==204)x.sendResponseHeaders(status,-1);else {byte[] bytes=JSON.writeValueAsBytes(body);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);}
                }catch(Exception ignored){}finally{x.close();}
            });STUB.start();
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void clients(DynamicPropertyRegistry r){
        for(String name:List.of("user","content","notification","payment"))r.add("erudit.admin.clients."+name+"-url",()->"http://127.0.0.1:"+STUB.getAddress().getPort());
    }
    @AfterAll static void close(){STUB.stop(0);POOL.close();}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    @BeforeEach void reset(){jdbc.execute("TRUNCATE admin_audit");MODE.set(0);ENQUEUES.set(0);BLOCKS.set(0);LAST_REQUEST_ID.set(null);}
    @Test void usersAndBlockAreProxiedAndAuditPreservesActorAndRequestId()throws Exception{
        mvc.perform(get("/api/v1/admin/users").header("X-Request-ID","admin-90-list")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count","1")).andExpect(jsonPath("$.data[0].name").value("Demo"));
        mvc.perform(post("/api/v1/admin/users/{id}/block",USER).header("X-User-Id",OTHER).header("X-Request-ID","admin-90-block")).andExpect(status().isNoContent());
        assertThat(BLOCKS.get()).isOne();assertThat(LAST_REQUEST_ID.get()).isEqualTo("admin-90-block");
        mvc.perform(get("/api/v1/admin/audit").param("actorId",OTHER.toString()).param("action","USER_BLOCKED"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count","1")).andExpect(jsonPath("$.data[0].metadata.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].metadata.requestId").value("admin-90-block"));
    }
    @Test void contentAndPaymentViewsUseFeignAndKeepPageCounts()throws Exception{
        mvc.perform(get("/api/v1/admin/content/moderation")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count","1"));
        for(String action:List.of("approve","reject"))mvc.perform(post("/api/v1/admin/content/{id}/"+action,ITEM)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(ITEM.toString()));
        mvc.perform(get("/api/v1/admin/content/reports")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count","0"));
        mvc.perform(post("/api/v1/admin/content/reports/{id}/resolve",ITEM).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("decision","REJECT")))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/users/{id}/subscription",USER)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/admin/users/{id}/payments",USER)).andExpect(status().isOk()).andExpect(header().string("X-Total-Count","1")).andExpect(jsonPath("$.data[0].amount").value(299));
    }
    private String batch(List<UUID> users,boolean parameters){var value=new HashMap<String,Object>();value.put("userIds",users);value.put("templateId",TEMPLATE);if(parameters)value.put("parameters",Map.of("name","Student"));return JSON.writeValueAsString(value);}
    @Test void sendManyDeduplicatesRecipientsAndOnlyUsesLocalQueue()throws Exception{
        mvc.perform(post("/api/v1/admin/notifications/send-many").contentType(MediaType.APPLICATION_JSON).content(batch(List.of(USER,USER),true))).andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(1));
        assertThat(ENQUEUES.get()).isOne();
    }
    @Test void partialFailureIsStructuredAndSurvivesInLocalAudit()throws Exception{
        MODE.set(6);
        mvc.perform(post("/api/v1/admin/notifications/send-many").header("X-Request-ID","partial-90").contentType(MediaType.APPLICATION_JSON).content(batch(List.of(USER,OTHER),true)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("PARTIAL_FAILURE")).andExpect(jsonPath("$.details.sent").value(1));
        mvc.perform(get("/api/v1/admin/audit")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].metadata.status").value("PARTIAL")).andExpect(jsonPath("$.data[0].metadata.sent").value(1));
        assertThat(ENQUEUES.get()).isEqualTo(2);
    }
    @Test void unavailableServiceAndTimeoutProduceUnknownAuditWithoutRetries()throws Exception{
        MODE.set(1);mvc.perform(get("/api/v1/admin/users")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT status FROM admin_audit",String.class)).isEqualTo("UNKNOWN");
        MODE.set(3);mvc.perform(get("/api/v1/admin/users")).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit WHERE status='UNKNOWN'",Integer.class)).isEqualTo(2);
    }
    @Test void malformedResponseAndRejectedWritesAreAudited()throws Exception{
        MODE.set(2);mvc.perform(get("/api/v1/admin/users")).andExpect(status().isServiceUnavailable());
        MODE.set(4);mvc.perform(post("/api/v1/admin/users/{id}/block",USER)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("REMOTE_REJECTED"));
        assertThat(BLOCKS.get()).isOne();assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit WHERE status='FAILED'",Integer.class)).isOne();
    }
    @Test void disabledAndMissingTemplateParametersCauseNoDelivery()throws Exception{
        mvc.perform(post("/api/v1/admin/notifications/send-many").contentType(MediaType.APPLICATION_JSON).content(batch(List.of(USER),false))).andExpect(status().isBadRequest());
        MODE.set(5);mvc.perform(post("/api/v1/admin/notifications/send-many").contentType(MediaType.APPLICATION_JSON).content(batch(List.of(USER),true))).andExpect(status().isConflict());
        assertThat(ENQUEUES.get()).isZero();
    }
    @Test void invalidPagesFiltersAndPayloadsAreRejected()throws Exception{
        for(String path:List.of("/api/v1/admin/audit","/api/v1/admin/users","/api/v1/admin/content/moderation","/api/v1/admin/content/reports","/api/v1/admin/users/"+USER+"/payments"))
            mvc.perform(get(path).param("size","51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/audit").param("from","2026-11-05T00:00:00Z").param("to","2026-11-04T00:00:00Z")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/notifications/send-many").contentType(MediaType.APPLICATION_JSON).content(batch(List.of(),true))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/audit").param("page","-1")).andExpect(status().isBadRequest());
        assertThat(ENQUEUES.get()).isZero();
    }
}
