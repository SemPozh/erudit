# Финальная приёмка и демонстрация ЛР2 (97, 100)

Дата приёмки: 5 ноября 2026, Europe/Moscow. База: `2a8ddfd` (merged PR 83).
В системе 8 бизнес-приложений, Gateway, Config Server и Eureka; 7 PostgreSQL и
ClickHouse. Текущий контракт: [83 public + 12 internal operations](api/endpoints.md).
Параметры, DTO, ответы и примеры публикуются в Gateway Swagger.

## Матрица требований

| Требование | Реализация | Проверка / доказательство |
| --- | --- | --- |
| Eureka | [standalone registry][eureka], Config Server + Gateway + 8 owners регистрируются по service ID | [RegistryTest][registry-test], [smoke-acceptance.ps1][acceptance]: 10 UP clients и readiness registry |
| Config Server | [native bootstrap][config], read-only config repository; 10 клиентов используют обязательный config import | [ConfigClientTest][config-test]: загрузка изменённой конфигурации после restart и fail-fast; acceptance проверяет успешный import в startup logs и непустой remote environment |
| Gateway | [маршрутизация][gateway], public-only OpenAPI, CORS/request ID, фильтр утверждённых операций | [GatewayHttpTest][gateway-test], [Swagger smoke][swagger], 50 HTTP contract checks |
| Feign + discovery | [InternalClients][clients], [IntegrationAutoConfiguration][integration], отдельные HTTP DTO | [IntegrationAutoConfigurationTest][integration-test], Admin/User integration, E2E premium/inbox |
| Circuit Breaker | [ResilientExecutor][executor], AI local fallback, payment fail-closed, Config Server timeouts | [AI integration][ai-test], [resilience smoke][resilience]: dependency stop, OPEN, fallback, restart, CLOSED, premium recovery |
| Reactor + R2DBC | [EventIngestionService][events], [EventReceiptRepository][receipts], bounded event pipeline и идемпотентность | [EventStoresTestcontainersTest][event-test], [EventIngestionServiceTest][event-unit], HTTP duplicate event + analytics E2E |
| Reactor + JDBC | [NotificationService][notifications]: Mono.fromCallable + dedicated boundedElastic | [NotificationPostgresTest][notification-test], [NotificationServiceTest][notification-unit], inbox/read E2E |
| Разделение БД и доменов | schema/миграции/credentials принадлежат owner; общих Entity нет | [DatabaseIsolationTest][database-test], [DataOwnershipArchitectureTest][ownership-test], [ServiceLayersTest][layers] |
| ЛР2 без auth и внешней отправки | X-User-Id только demo context; [MockNotificationSender][mock], mock payment и AI; private internal network | [MockNotificationSenderTest][mock-test], ArchUnit запрет Spring Security/JWT, Gateway contract assertions, локальные E2E |
| CI gate | module tests/coverage, root, OpenAPI/build, 11 images, infrastructure/system smoke → aggregate test | [CI workflow][ci], [зелёный полный прогон PR 83][ci-proof]; main protected, required test/build для everyone |

Config Server сам является источником конфигурации: он загружает native repository
при bootstrap и не импортирует себя по HTTP. Eureka — standalone сервер с
`register-with-eureka=false`, `fetch-registry=false`; он проверяется через readiness,
а не фиктивную регистрацию в собственной registry. Итого 11 приложений = 10 Eureka clients + registry; независимо от этого,
11 приложений = 10 Config clients + native Config Server. Эти роли проверяются
отдельно, без создания циклических startup dependencies.

## Почему такие границы

User владеет профилем/социальными связями; Content — публикациями, media и прогрессом;
Quiz — assessment/program/quiz/competition/rating как единым учебным контекстом.
Payment хранит тариф, подписку и платёж атомарно у одного владельца. Notificator
владеет inbox и mock delivery; Event Receiver — квитанциями и аналитикой.
AI возвращает результат, который сохраняет вызывающий домен. Admin — фасад с
собственным аудитом, координирующий владельцев через HTTP, без чужих SQL/Entity.
Gateway/Config/Eureka не хранят бизнес-данные. Детали: [lab2-architecture.md](lab2-architecture.md).

Межсервисный вызов не является распределённой ACID-транзакцией. Ошибки зависимостей
явно преобразуются в fallback/503; локальные инварианты фиксирует владелец БД.

| Составное изменение | Граница транзакции и проверка |
| --- | --- |
| Content moderation / report resolution | [ContentWorkflowService][content-tx]: состояние, resolution и audit в одной локальной транзакции; [ContentWorkflowIntegrationTest][content-test] проверяет rollback при failed audit и concurrent resolution |
| Mock purchase | [SubscriptionService][payment-tx]: owner lock, payment и activation через TransactionTemplate; [SubscriptionIntegrationTest][payment-test] проверяет конкуренцию/rollback |
| Assessment submit | [AssessmentService][assessment-tx]: результат и learning program сохраняются одной транзакцией; [AssessmentRepositoryTestcontainersTest][assessment-test] |
| Admin operation audit | [AdminAuditWriter][admin-tx]: REQUIRES_NEW для STARTED/итога; удалённый side effect не откатывается транзакцией Admin; [AdminFacadeIntegrationTest][admin-test] |
| Событие → аналитика | R2DBC receipt и ClickHouse не имеют общей транзакции: duplicate eventId, ограниченный pipeline, retry записи и delivered/failed; это учебный pipeline, не durable broker/outbox |

## Scheduler isolation

HTTP Notificator выполняет JDBC на `notification-jdbc` boundedElastic (threads/queue
ограничены конфигурацией), а не на Netty event loop. ClickHouse JDBC — на отдельном
`clickhouse-jdbc` boundedElastic(4,256); R2DBC квитанции остаются неблокирующими.
[NotificationScheduler][notification-scheduler] работает в Spring scheduling thread,
обрабатывает ограниченную партию и использует локальный mock provider; его JDBC/Feign
не попадают на reactive HTTP thread. [SubscriptionMaintenance][maintenance] — отдельная
периодическая задача Payment. AI timeout/rate-limit ограничивают ожидание mock provider.
Размеры очередей и ошибки не скрывают гарантии: между процессами нет exactly-once,
Kafka/реальные уведомления относятся к следующим ЛР. Смена scheduler не создаёт
транзакцию автоматически: транзакция открывается владельцем в рабочем потоке.

## Воспроизведение с чистого demo

Нужны Docker Desktop/Engine + Compose v2, PowerShell 7.5+, Java 25 для локального Gradle.
Из корня репозитория:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4' # локальный путь к установленной Java 25
./gradlew test openApiValidate build
./scripts/reset-demo.ps1
./scripts/smoke-acceptance.ps1
./scripts/smoke-demo-contracts.ps1
./scripts/smoke-e2e.ps1
./scripts/smoke-resilience.ps1
```

Reset собирает/проверяет 11 images (Git SHA/UTC/digest/UID 10001), затем создаёт
чистые demo volumes в проекте `erudit-demo`, порт 18089. Он удаляет только выбранный
demo project; основной `erudit` и его данные не сбрасываются. `-SkipBuild` разрешён,
когда текущие образы уже собраны. Для обычного запуска без reset:

```powershell
$env:GATEWAY_PORT=18089
docker compose -p erudit-demo -f docker-compose.yml -f docker-compose.demo.yml --profile demo up --build -d --wait
```

После container health дождитесь успешного `smoke-acceptance`/HTTP smoke: Eureka и
кеши клиентов/Gateway обновляются асинхронно. Swagger:
[http://localhost:18089/swagger-ui/index.html](http://localhost:18089/swagger-ui/index.html).
Сценарии/fixture IDs и порядок действий: [demo-runbook.md](demo-runbook.md).
Ни одна команда не требует ручного получения ID из БД. Authorization не передаётся.
Для смены контекста используйте X-User-Id, который не удостоверяет личность.

Resilience автоматически останавливает AI и payment только в указанном локальном
demo project. Проверяются локальная генерация, OPEN gauge, доступность free content,
premium masking, затем рестарт и CLOSED gauge. Gateway и Content восстанавливают
свои discovery caches независимо: успешный plans в Gateway ещё не означает premium
recovery в Content. finally восстанавливает зависимости и архивирует собственные probes.
Полные request/response traces и schema validation остаются включены.

Normal-profile smoke использует отдельный временный project со свежими томами,
проверяет 19 healthy containers/порты/fixtures и User restart без rebuild:

```powershell
docker compose -p erudit-demo -f docker-compose.yml -f docker-compose.demo.yml --profile demo stop
./scripts/smoke-platform.ps1 -ImageProject erudit-demo
$env:GATEWAY_PORT=18089
docker compose -p erudit-demo -f docker-compose.yml -f docker-compose.demo.yml --profile demo up -d --no-build --wait
```

Восстановление demo всегда задаёт порт 18089. Временные normal volumes удаляет
сам smoke; существующие основные тома не затрагиваются.

## Результаты и свидетельства

[CI PR 83, run 219][ci-proof]: все 19 jobs SUCCESS, включая test/build, coverage,
OpenAPI, infrastructure и full system smoke. 161 тест без failures/skips;
50 HTTP status/contract checks, 33 E2E шага, 5 resilience assertions,
normal-profile 19 healthy + User restart. [Пошаговый отчёт](gateway-e2e-results.md).
В этой задаче к root suite добавлена защита каталога endpoints от рассинхронизации.

Локальный прогон этой ветки 5 ноября 2026 (чистые demo volumes, `reset-demo -SkipBuild`,
повторно использованы проверенные application binaries; production Java не менялась):

| Проверка | Результат |
| --- | --- |
| test / OpenAPI / coverage / build | PASS: 162 tests, 0 failures, 0 skipped |
| API inventory --check и все ссылки матрицы | PASS: 95 service operations, пути существуют |
| Eureka + Config + Swagger acceptance | PASS: все 11 приложений с учётом bootstrap/registry ролей |
| HTTP contracts | PASS: 50 checks |
| Gateway E2E | PASS: 33 steps, без Authorization/SQL |
| AI/payment failure + recovery | PASS: 5 checks, OPEN/CLOSED и premium recovery |

CI повторяет эти сценарии с полной пересборкой 11 images и normal-profile smoke.

Отчёты нового воспроизводимого запуска: `build/acceptance-platform.json`,
`build/demo-contract-results.xml`, `build/gateway-e2e.xml`/`.xml.md`,
`build/gateway-resilience.md`, `build/images-report.json`; CI сохраняет их как artifact.
Config/Eureka проверяются diagnostic HTTP внутри контейнеров, бизнес-цепочки —
только Gateway. Отчёт acceptance публикует только PASS/FAIL и имена приложений,
не содержимое конфигурации и не credentials.

GitHub API на 5 ноября 2026 подтвердил [main protection][branch-proof]:
`protected=true`, required `test` и `build`, `enforcement_level=everyone`, app_id 15368
(GitHub Actions). Это закрывает последний критерий 97: aggregate test краснеет
при любом failed/skipped prerequisite; GitHub требует этот check для merge.
Снимок ограниченных полей: [main-protection.json](history/main-protection.json).
Полные administration settings текущему аккаунту недоступны; отдельное утверждение
об отсутствии любых repository ruleset bypass actors здесь не делается.

[eureka]: ../infrastructure/erudit-discovery-server/src/main/java/com/erudit/discoveryserver/ServiceApplication.java
[registry-test]: ../infrastructure/erudit-discovery-server/src/test/java/com/erudit/discoveryserver/RegistryTest.java
[config]: ../infrastructure/erudit-config-server/src/main/resources/application.properties
[config-test]: ../infrastructure/erudit-config-server/src/test/java/com/erudit/configserver/ConfigClientTest.java
[gateway]: ../infrastructure/erudit-gateway/src/main/java/com/erudit/gateway/GatewayConfiguration.java
[gateway-test]: ../infrastructure/erudit-gateway/src/test/java/com/erudit/gateway/GatewayHttpTest.java
[clients]: ../platform/erudit-integration/src/main/java/com/erudit/integration/InternalClients.java
[integration]: ../platform/erudit-integration/src/main/java/com/erudit/integration/IntegrationAutoConfiguration.java
[integration-test]: ../platform/erudit-integration/src/test/java/com/erudit/integration/IntegrationAutoConfigurationTest.java
[executor]: ../platform/erudit-integration/src/main/java/com/erudit/integration/ResilientExecutor.java
[ai-test]: ../services/erudit-quiz/src/test/java/com/erudit/quiz/AiQuizIntegrationTest.java
[events]: ../services/erudit-event-receiver/src/main/java/com/erudit/eventreceiver/service/EventIngestionService.java
[receipts]: ../services/erudit-event-receiver/src/main/java/com/erudit/eventreceiver/repository/EventReceiptRepository.java
[event-test]: ../services/erudit-event-receiver/src/test/java/com/erudit/eventreceiver/EventStoresTestcontainersTest.java
[event-unit]: ../services/erudit-event-receiver/src/test/java/com/erudit/eventreceiver/EventIngestionServiceTest.java
[notifications]: ../services/erudit-notificator/src/main/java/com/erudit/notificator/service/NotificationService.java
[notification-test]: ../services/erudit-notificator/src/test/java/com/erudit/notificator/NotificationPostgresTest.java
[notification-unit]: ../services/erudit-notificator/src/test/java/com/erudit/notificator/NotificationServiceTest.java
[notification-scheduler]: ../services/erudit-notificator/src/main/java/com/erudit/notificator/service/NotificationScheduler.java
[maintenance]: ../services/erudit-payment/src/main/java/com/erudit/payment/service/SubscriptionMaintenance.java
[mock]: ../services/erudit-notificator/src/main/java/com/erudit/notificator/service/MockNotificationSender.java
[mock-test]: ../services/erudit-notificator/src/test/java/com/erudit/notificator/MockNotificationSenderTest.java
[database-test]: ../src/test/java/com/erudit/architecture/DatabaseIsolationTest.java
[ownership-test]: ../src/test/java/com/erudit/architecture/DataOwnershipArchitectureTest.java
[layers]: ../src/test/java/com/erudit/architecture/ServiceLayersTest.java
[content-tx]: ../services/erudit-content/src/main/java/com/erudit/content/service/ContentWorkflowService.java
[content-test]: ../services/erudit-content/src/test/java/com/erudit/content/ContentWorkflowIntegrationTest.java
[payment-tx]: ../services/erudit-payment/src/main/java/com/erudit/payment/service/SubscriptionService.java
[payment-test]: ../services/erudit-payment/src/test/java/com/erudit/payment/SubscriptionIntegrationTest.java
[assessment-tx]: ../services/erudit-quiz/src/main/java/com/erudit/quiz/service/AssessmentService.java
[assessment-test]: ../services/erudit-quiz/src/test/java/com/erudit/quiz/AssessmentRepositoryTestcontainersTest.java
[admin-tx]: ../services/erudit-admin/src/main/java/com/erudit/admin/service/AdminAuditWriter.java
[admin-test]: ../services/erudit-admin/src/test/java/com/erudit/admin/AdminFacadeIntegrationTest.java
[acceptance]: ../scripts/smoke-acceptance.ps1
[swagger]: ../scripts/smoke-swagger.ps1
[resilience]: ../scripts/smoke-resilience.ps1
[ci]: ../.github/workflows/ci.yml
[ci-proof]: https://github.com/SemPozh/erudit/actions/runs/41933984993
[branch-proof]: https://api.github.com/repos/SemPozh/erudit/branches/main
