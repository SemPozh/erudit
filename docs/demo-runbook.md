# Demo ЛР2: задачи 94, 95, 98

Из корня репозитория (Docker Compose v2, Java 25 для локальных тестов,
PowerShell 7.5+ для скриптов):

```powershell
./scripts/reset-demo.ps1
./scripts/smoke-demo-contracts.ps1
```

Reset создаёт отдельный проект `erudit-demo` на порту 18089. Swagger:
`http://localhost:18089/swagger-ui/index.html`. Можно выбрать `-Project erudit-demo-ci`
или другой суффикс `erudit-demo-...`, `-Port 18090`; другие имена отклоняются до
вызова Docker. `-SkipBuild` повторно использует уже собранные образы этого проекта.
Reset удаляет только контейнеры/тома выбранного demo-проекта, проверяет labels
томов и заново применяет fixtures. Основной проект `erudit` не останавливается.
Production reset endpoints нет. Demo-тома не следует повторно использовать с
выключенным demo-профилем: для обычного окружения создайте отдельный проект.

Полная команда demo-профиля без reset:

```powershell
$env:GATEWAY_PORT=18089
docker compose -p erudit-demo -f docker-compose.yml -f docker-compose.demo.yml --profile demo up --build -d --wait
```

Авторизации нет. Заголовок `X-User-Id` — непроверяемый контекст, по умолчанию
`00000000-0000-0000-0000-000000000001`. Реальные email/push/SMS не отправляются.
Payment/AI/notificator работают локально, бизнес-контейнеры находятся только во
внутренней сети, доступа во внешнюю сеть у них нет. Старые миграции уже содержат
базовые тарифы/категории/daily quiz/competition; новые fixtures подключаются только
через `application-demo.properties`. Данные сохраняет только владелец домена.

| Ресурс | Детерминированный ID / значение |
| --- | --- |
| Пользователь | 00000000-0000-0000-0000-000000000001 |
| Друг / участник приглашения | 00000000-0000-0000-0000-000000000002 |
| Ожидающий запрос дружбы | f0000000-0000-0000-0000-000000000001, получатель user ...003 |
| Push device | d0000000-0000-0000-0000-000000000001 |
| Категория | 10000000-0000-0000-0000-000000000001 |
| Опубликованная статья / карточка | 11000000-0000-0000-0000-000000000001 / 13000000-0000-0000-0000-000000000001 |
| Статья для модерации | 11000000-0000-0000-0000-000000000002 |
| Premium статья | 11000000-0000-0000-0000-000000000003 |
| Открытая жалоба | 12000000-0000-0000-0000-000000000001 |
| Assessment result / STARTED session | 51000000-0000-0000-0000-000000000001 / ...002 |
| Daily quiz / STARTED attempt | 20000000-0000-0000-0000-000000000001 / 52000000-0000-0000-0000-000000000001 |
| Competition | 30000000-0000-0000-0000-000000000001 |
| Тариф / истёкшая подписка | PREMIUM_MONTHLY / 00000000-0000-0000-0000-000000008700 |
| Mock paid payment | 00000000-0000-0000-0000-000000009400 |
| Notification template / inbox item | 40000000-0000-0000-0000-000000000001 / 41000000-0000-0000-0000-000000000001 |
| Audit | 94000000-0000-0000-0000-000000000001, request ID demo-94 |
| Analytics events | e0000000-0000-0000-0000-000000000001 … 005, 2026-01-01T12:00:00Z |

## Порядок ручных сценариев в Swagger

1. User: GET users/settings/friends, PUT профиль/настройки. Нового пользователя
   создайте register, затем verify с `demo-verification-token`; для DELETE используйте
   ID нового пользователя. Примеры FriendRequest/DeviceRequest содержат локальные
   значения. Решение pending request выполняется с `X-User-Id` user ...003.
2. Content: GET статьи/cards/feed, POST view/complete. Для CRUD категории создайте
   новую категорию, PUT и DELETE её (базовые категории связаны с контентом).
   POST content возвращает новый ID со статусом PENDING_MODERATION; одобрите его
   через Admin, GET/PUT, снова одобрите и DELETE. Media: POST multipart file, затем
   используйте возвращённые ID/URL для GET — ручного SQL не требуется.
3. Admin: очередь содержит статью ...002. Approve или reject меняют её состояние.
   Report ...001 разрешается один раз; REJECT сохраняет статью ...001 опубликованной,
   ACCEPT архивирует её. После этого новый report можно создать через Content.
4. Quiz: вопросы assessment уже существуют; session ...001 даёт готовый result,
   session ...002 принимает SubmitRequest. STARTED attempt 520...001 принимает hint
   (не более двух) и submit с optionIndex=1 один раз; повторный submit даёт 409.
   Новые sessions/attempts создаются POST-операциями, используйте возвращённые ID.
   Competition invite использует друга user ...002; join выполняйте от его имени.
5. Payment: истёкшая подписка не открывает premium. POST subscriptions с
   PREMIUM_MONTHLY/mock_card активирует локальную подписку; используйте возвращённый
   ID для cancel. История уже содержит mock-платёж. Webhook example/HMAC для
   истёкшей подписки описаны также в `docs/payment-events.md` (только mock secret).
6. Notificator: inbox item можно mark-read; шаблон ...001 работает для Admin
   send-many. Создайте отдельный шаблон для PUT/DELETE, сохранив базовый DEMO.
7. Analytics: from=2026-01-01T00:00:00Z, to=2026-01-02T00:00:00Z. Пять событий
   поступают через обычный EventIngestionService. Повторный запуск не дублирует их;
   EventRequest в Swagger имеет отдельный ID ...099 для POST и повторной проверки.

Stateful операции меняют fixtures; для повторения сценария выполните reset.
Свежий media-volume наследует владельца appuser из образа; upload/download
проверяются через multipart и побайтовое сравнение, ручной chown не нужен.

HTTP suite намеренно меняет moderation/report/attempt/read-state. Его повторяют
после reset. Публичные OpenAPI содержат валидные request examples и path-ID examples.
Все GET/POST/PUT/DELETE доступны через Gateway/Swagger без SQL или psql.

## Compose и диагностика

`docker compose up --build -d --wait` запускает обычное окружение: все восемь
бизнес-сервисов, Gateway, Config, Eureka, семь PostgreSQL и ClickHouse. Наружу
опубликован только Gateway. `scripts/smoke-platform.ps1` проверяет обычный профиль на свежих томах с теми же
образами, все 19 healthy, публикацию портов и restart User. Перед его запуском
остановите demo-контейнеры (`docker compose -p erudit-demo -f docker-compose.yml
-f docker-compose.demo.yml --profile demo stop`), чтобы не держать два полных стека
одновременно. `docker compose restart user-service` не пересобирает
образы и не требует перезапуска остальных сервисов.

Debug opt-in: `docker compose -f docker-compose.yml -f docker-compose.debug.yml
--profile debug up -d --wait`. Config/Eureka доступны только через loopback
18888/18761. Обычный режим и demo не публикуют базы или внутренние HTTP-порты.

Команды диагностики: `docker compose ps`, `docker compose logs --tail 100 <service>`,
`docker compose exec <service> curl -fsS http://localhost:<port>/actuator/health/readiness`.
Каждый JVM имеет лимит 640 MiB/1 CPU/256 PID, PostgreSQL 256 MiB/0.5 CPU,
ClickHouse 1 GiB/2 CPU/512 PID, фоновый schedule pool 16 для образа 25.8. JAVA_TOOL_OPTIONS ограничивает heap половиной доступной
памяти и ActiveProcessorCount=2. Бюджет лимитов полного набора около 10 GiB плюс
Docker; при одновременном запуске другого проекта учитывайте его потребление.
BuildKit cache Gradle с sharing=locked исключает повторную загрузку зависимостей;
reset собирает образы партиями максимум по два.

## Проверки и CI

`./gradlew test openApiValidate build` запускает PostgreSQL/R2DBC/ClickHouse
Testcontainers владельцев, HTTP/Feign stub tests, guards границ/fixtures/OpenAPI,
проверки ошибок, rollback, fallback и pagination. Generated DTO исключены из
JaCoCo, порог 70% сохранён. CI запускает каждый модуль независимо через test/check,
публикует JUnit/HTML/JaCoCo даже при ошибке и отдельно выполняет demo HTTP suite.
Suite сверяет ответы с публичными schemas (required/type/enum/format/allOf/ref),
проверяет отрицательные pagination/sort, CRUD, state transitions, conflict,
idempotency и наличие demo-аналитики. JUnit: `build/demo-contract-results.xml`.
