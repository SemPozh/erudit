package com.erudit.eventreceiver;

import static org.assertj.core.api.Assertions.assertThat;

import com.erudit.eventreceiver.model.EventCommand;
import com.erudit.eventreceiver.repository.EventReceiptRepository;
import com.erudit.eventreceiver.service.ClickHouseEventStore;
import com.erudit.eventreceiver.service.EventIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

@Testcontainers
class EventStoresTestcontainersTest {
    private static final Map<String, Object> PROPS = loadTestProperties();
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(image("test.containers.postgres.image"));
    @Container static final ClickHouseContainer CLICKHOUSE = new ClickHouseContainer(image("test.containers.clickhouse.image"));
    static DatabaseClient database;
    static ClickHouseEventStore store;

    @BeforeAll static void schema() throws Exception {
        String driver = String.valueOf(PROPS.get("test.r2dbc.driver"));
        String clickhouseSchema = String.valueOf(PROPS.get("erudit.clickhouse.schema"));

        var options = ConnectionFactoryOptions.builder()
                .option(DRIVER, driver)
                .option(HOST, POSTGRES.getHost())
                .option(PORT, POSTGRES.getFirstMappedPort())
                .option(DATABASE, POSTGRES.getDatabaseName())
                .option(USER, POSTGRES.getUsername())
                .option(PASSWORD, POSTGRES.getPassword())
                .build();
        database = DatabaseClient.create(ConnectionFactories.get(options));
        var flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load();
        flyway.migrate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        store = new ClickHouseEventStore(CLICKHOUSE.getJdbcUrl(), CLICKHOUSE.getUsername(), CLICKHOUSE.getPassword(), clickhouseSchema, new ObjectMapper());
        store.initialize().block();
    }

    @Test void deduplicatesAndAggregatesWithoutBlockingEventLoop() throws Exception {
        var repository=new EventReceiptRepository(database,new ObjectMapper());var service=new EventIngestionService(repository,store);
        set(service,"retries",1);set(service,"buffer",16);service.start();UUID id=UUID.randomUUID();
        var event=new EventCommand(id,UUID.randomUUID(),"SESSION_STARTED",OffsetDateTime.now(),Map.of("source","test"));
        StepVerifier.create(service.ingest(event)).assertNext(v->assertThat(v.duplicate()).isFalse()).verifyComplete();
        StepVerifier.create(service.ingest(event)).assertNext(v->assertThat(v.duplicate()).isTrue()).verifyComplete();
        Thread.sleep(500);
        StepVerifier.create(store.aggregate("sessions",OffsetDateTime.now().minusMinutes(1),OffsetDateTime.now().plusMinutes(1)))
                .assertNext(v->{assertThat(Thread.currentThread().getName()).startsWith("clickhouse-jdbc");assertThat(v.count()).isEqualTo(1);assertThat(v.users()).isEqualTo(1);}).verifyComplete();
    }
    @Test void demoStartupIsIdempotentAcrossRestarts() throws Exception {
        var repository = new EventReceiptRepository(database,new ObjectMapper());
        var service = new EventIngestionService(repository,store);
        set(service,"retries",1);set(service,"buffer",16);service.start();
        var demo = new com.erudit.eventreceiver.demo.DemoEvents(service);
        demo.run(null);demo.run(null);
        Long receipts = database.sql("SELECT count(*) FROM event_receipts WHERE payload LIKE '%demo-94%'")
                .map((row,metadata) -> row.get(0,Long.class)).one().block();
        assertThat(receipts).isEqualTo(5);
        var from=OffsetDateTime.parse("2026-01-01T00:00:00Z");var to=from.plusDays(1);
        long count=0;
        for(int attempt=0;attempt<50;attempt++){
            count=store.aggregate("active-users",from,to).block().count();
            if(count==5)break;
            Thread.sleep(100);
        }
        assertThat(count).isEqualTo(5);
    }
    private static void set(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    private static Map<String,Object> loadTestProperties(){
        try {
            return (Map<String,Object>) new org.springframework.boot.env.YamlPropertySourceLoader()
                    .load("test-config", new org.springframework.core.io.ClassPathResource("application-test.yml")).get(0).getSource();
        } catch (Exception e) { throw new IllegalStateException("Cannot load application-test.yml", e); }
    }
    private static String image(String key){ return String.valueOf(PROPS.get(key)); }
}
