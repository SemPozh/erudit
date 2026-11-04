# Mock receipt и webhook (задача 87)

Payment service обрабатывает только локальные mock-сценарии. SDK, ключей и
сетевых запросов к настоящим платёжным провайдерам нет. Feign используется
только для проверки пользователя в erudit-user.

## Mobile receipt

`POST /api/v1/subscriptions/mobile/verify`:

```json
{"provider":"APP_STORE","receipt":"monthly-receipt-demo-87"}
```

Поддержаны `APP_STORE` и `GOOGLE_PLAY`. Любая непустая строка до 4096 символов,
не начинающаяся с `invalid` (без учёта регистра), является mock-чеком.
Подстрока `year` выбирает PREMIUM_YEARLY; иначе PREMIUM_MONTHLY. ID чека
детерминирован по UTF-8 содержимому и хранится отдельно для каждого provider.
Receipt, подписка и PAID payment сохраняются в одной транзакции.

Повтор возвращает ту же подписку, не меняя даты и не добавляя платёж. После
отмены или истечения повтор не восстанавливает premium. Применение того же чека
другим пользователем даёт 409. Новый чек не сокращает уже оплаченный срок.
Недоступность user service даёт 503 до изменения данных.

## Webhook

`POST /api/v1/payments/webhooks/{provider}`, header `X-Signature`:

```json
{"eventId":"demo-renewal-87","occurredAt":"2026-11-04T12:00:00Z","subscriptionId":"00000000-0000-0000-0000-000000008700","type":"RENEWED"}
```

Поддержаны только mock-адаптеры `MOCK`, `APP_STORE`, `GOOGLE_PLAY` и типы
`RENEWED`, `CANCELLED`, `EXPIRED`. Подпись — hex HMAC-SHA256 от компактного JSON
с рекурсивно отсортированными ключами. Для стандартного публичного demo key
`local-webhook-secret` подпись примера:

```text
4733679dfa15539535a042cc17b59898a29775016d638eca845beb47a1251465
```

Config Server получает `erudit.payments.webhook-secret` через
`${PAYMENT_WEBHOOK_SECRET}`; Compose задаёт публичный mock default. В Config
Server также опубликованы примеры receipt, ключа и подписи. В Swagger payment
service заполнены тело запроса, provider, X-User-Id и тестовая подпись.
Если меняется тело или ключ, подпись нужно пересчитать.

Повтор `(provider,eventId)` с тем же телом возвращает 204 без нового платежа.
Повтор с другим телом даёт 409. Только событие с более новым `occurredAt`
меняет состояние; при равном времени действует первое принятое событие.
Событие до покупки и старое событие после локальной отмены/нового receipt
игнорируются. Событие прежней подписки не восстанавливает её поверх более
новой покупки. Отмена/истечение не добавляют платёж; renewal продлевает срок
на 30/365 дней и создаёт PAID payment. Недоступность тарифа для новых покупок
не мешает обслуживать существующую подписку.

Idempotency, смена статуса, даты и новый payment фиксируются атомарно.
При ошибке БД квитанция откатывается вместе с остальными изменениями, и запрос
можно повторить. Неверная подпись или поля возвращают 400, неизвестная подписка — 404.

## Compose и smoke

Явный override включает Spring profile demo и отдельную временную БД в tmpfs.
Обычный payment volume сохраняется. Demo Flyway migration устанавливает
фиксированную EXPIRED подписку из примера; сама миграция premium не выдаёт.

```powershell
docker compose -f docker-compose.yml -f docker-compose.payment-demo.yml up --build -d --wait payment-service payment-demo-postgres
.\scripts\smoke-payment-events.ps1
```

После холодного запуска дайте Eureka обновить кэши; до этого проверка
пользователя может корректно вернуть 503. Smoke рассчитан на чистую временную
demo БД и проверяет подпись примера, повтор webhook, receipt и повтор после отмены.
Для нового прогона можно пересоздать только временную БД и payment-service;
состояние tmpfs при остановке БД теряется. Возврат к обычной БД:

```powershell
docker compose up -d --no-deps --wait payment-service
```

Проверка кода: `gradlew test openApiValidate build`. PostgreSQL/Testcontainers
тесты покрывают конкурентное использование receipt двумя пользователями,
дубли webhook, порядок событий и rollback при отказе записи платежа.
