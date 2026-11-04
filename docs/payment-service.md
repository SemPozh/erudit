# Payment service (ЛР2, задача 86)

`erudit-payment` владеет тарифами, подписками и платежами в PostgreSQL
`erudit_payment`. Flyway создаёт схему независимо от legacy и user service;
идентификатор пользователя не является внешним ключом в чужую БД.
Запись агрегатов выполняется Spring Data JDBC, выборки — JdbcClient.

## API и демонстрация

Локальный контракт: `services/erudit-payment/src/main/resources/static/openapi.yaml`.
Swagger UI сервиса использует `/openapi.yaml`. Внешние пути через Gateway:

- `GET /api/v1/subscriptions/plans` — публичные доступные тарифы;
- `POST /api/v1/subscriptions` — `{"plan":"PREMIUM_MONTHLY","paymentMethodId":"mock_card"}`;
- `GET /api/v1/subscriptions/me` — последняя подписка;
- `POST /api/v1/subscriptions/{id}/cancel` — идемпотентная отмена;
- `GET /api/v1/subscriptions/payments?page=0&size=20` — история с `X-Total-Count`.

`X-User-Id` — непроверяемый контекст ЛР2; значение по умолчанию:
`00000000-0000-0000-0000-000000000001`. Авторизации нет. Тарифы имеют постоянные
коды `PREMIUM_MONTHLY` (299 RUB / 30 дней) и `PREMIUM_YEARLY` (2990 RUB / 365 дней).
Все платежи локальные mock; `paymentMethodId` с префиксом `fail_` отклоняет оплату.
Реквизиты не сохраняются. Failed payment остаётся в истории без подписки.
Mobile receipt и webhook относятся к отдельной задаче 87.

`GET /internal/v1/entitlements/{userId}` возвращает `userId`, `premium`, `expiresAt`.
Gateway не маршрутизирует `/internal/**`. Content использует Feign-клиент этого API
и скрывает description/body/media при отсутствии premium или сбое payment.
Проверка существования пользователя выполняется Feign-вызовом
`GET /internal/v1/users/{id}` в user service: неизвестный пользователь даёт 404,
недоступный сервис — 503 без записи платежа. Таймауты и Circuit Breaker берутся
из общей конфигурации.

## Транзакции и обслуживание

Создание блокирует строку владельца подписок до конца транзакции: два
одновременных запроса не списывают два mock-платежа. Подписка и успешный платёж
фиксируются атомарно; ограничение БД дополнительно разрешает одну ACTIVE подписку.
Отмена проверяет владельца и обновляет статус в одной транзакции.
Entitlement проверяет границы времени независимо от планировщика. При чтении
текущей подписки и перед покупкой истёкшие записи получают EXPIRED. Планировщик
обрабатывает остальные записи с интервалом `erudit.payments.maintenance-interval`
(по умолчанию PT1H, начальная задержка PT1M). Настоящих уведомлений нет.

## Проверка

```powershell
.\gradlew.bat :services:erudit-payment:test :services:erudit-payment:build
.\gradlew.bat test openApiValidate build
docker compose up --build -d payment-service content-service gateway
.\scripts\smoke-payment.ps1
```

Smoke использует demo-пользователя, оформляет и отменяет его подписку, оставляя
платежи в истории. Интеграционные тесты используют отдельный PostgreSQL через
Testcontainers: создание, отмена, expiration, отказ оплаты, отсутствие user
service, rollback при ошибке БД и две конкурентные покупки. Content тестируется
с доступным и недоступным entitlement API.

После холодного запуска дождитесь регистрации сервисов и обновления кэшей Eureka.
Пока user ещё не обнаружен, покупка корректно возвращает 503 без записи платежа;
после прогрева discovery повторите smoke. Скрипт также создаёт premium-статью,
проверяет её доступность до и после отмены и архивирует тестовую статью.
