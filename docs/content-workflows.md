# Content workflows (ЛР2, задача 78)

Новый и изменённый материал получает PENDING_MODERATION. Публичное чтение,
feed, progress и quiz cards допускают только PUBLISHED; REJECTED и ARCHIVED
скрыты. Изменение любого существующего материала снова требует модерации.
Архивирование сохраняет историю прогресса и жалоб.

## Внешний API

- `POST /api/v1/content/{id}/view`, `/complete`, `GET .../progress` — прогресс
  текущего непроверяемого контекста X-User-Id (по умолчанию demo UUID ...0001).
- `GET /api/v1/content/{id}/quiz-cards` — до 5 сохранённых карточек на основе
  текста материала. При создании/изменении карточки заменяются в той же транзакции.
- `GET /api/v1/content/feed?page=0&size=20` — персонализация по взаимодействиям
  пользователя в БД content. Непросмотренные материалы имеют больший приоритет,
  завершённые уходят ниже; учитываются категории прошлых взаимодействий.
  Ответ имеет только data и заголовки X-Has-More/X-Next-Page, без total count.
- `POST /api/v1/content/{id}/report` — reason длиной 10–500 символов.
- `GET /api/v1/admin/content/moderation`, `/reports` — постраничные очереди,
  X-Total-Count и pagination с totalElements; size ограничен 50.
- `POST /api/v1/admin/content/{id}/approve`, `/reject` — однократное решение.
- `POST /api/v1/admin/content/reports/{id}/resolve` — decision ACCEPT/REJECT,
  необязательный comment до 2000 символов. ACCEPT архивирует опубликованный
  материал, REJECT оставляет его доступным. Повтор решения даёт 409.

Авторизации, проверки ролей, JWT и реальных уведомлений нет. X-User-Id в аудите
является только демонстрационным контекстом. Premium проверяется через Feign
Payment entitlement: без доступа progress/cards дают 403, feed и публичный
каталог скрывают платные поля. Сбой Payment означает premium=false.

## Границы и транзакции

Схема Flyway V3 содержит только собственные progress, quiz cards, reports и
moderation audit. Внешнего ключа в user/payment БД нет. Report inserts используют
Spring Data JDBC; условные изменения и выборки выполняются JdbcClient.

Изменение материала, модерация, архивирование, запись progress и рассмотрение
жалоб используют блокировку одной строки content. Таким образом, просмотр не
пишется поверх архивирования, два модератора не принимают два решения, а смена
контента и его quiz cards атомарна. Обратная запись VIEWED не сбрасывает COMPLETED;
повторы сохраняют исходные timestamps. Разрешение жалобы, архивирование и audit
фиксируются одной транзакцией и вместе откатываются при ошибке БД.

На этом этапе генератор карточек локальный, с отдельным интерфейсом
QuizCardGenerator для задачи 89. Запросы к AI здесь не добавляются.

## Интеграция Admin и Gateway

`POST /internal/v1/moderation` принимает contentId, status=PUBLISHED/REJECTED
и reason; ответ совместим с общим InternalClients.ModerationClient.
Внутренние GET `/internal/v1/content/moderation`, `/reports` и POST
`/internal/v1/content/reports/{id}/resolve` предназначены для будущего Admin facade.
Gateway не открывает /internal/**.

Gateway направляет `/api/v1/admin/**` в Admin facade (задача 90), который
обращается к внутренним Content API по Feign. Временные дубли внешних Admin routes внутри Content удалены в №99;
CORS открывает заголовки feed для браузера.

## Проверка

```powershell
.\gradlew.bat test openApiValidate build
docker compose up --build -d --wait content-service gateway payment-service
.\scripts\smoke-content-workflows.ps1
.\scripts\smoke-payment.ps1
```

Smoke создаёт собственный материал, проходит модерацию, progress, cards и
рассмотрение жалобы, оставляя fixture в ARCHIVED. PostgreSQL/Testcontainers
тесты проверяют конкуренцию, rollback, все статусы, premium fallback,
пользовательскую изоляцию и обе схемы пагинации.
