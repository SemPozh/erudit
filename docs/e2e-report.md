# E2E отчёт ЛР 1

Дата: 22 октября 2026 года. Окружение поднято командой
`docker compose up --build -d`: приложение, PostgreSQL и ClickHouse работают в
Docker. Swagger UI и `/openapi.yaml` ответили `200`.

Во всех ответах присутствовал `X-Request-ID`. `Authorization` не передавался:
в ЛР 1 авторизация отсутствует. Для пользовательских операций применялся только
непроверяемый `X-User-Id: 9f3c4e77-3131-428e-9c44-0f7126038c71`.

## Сценарий 1. Регистрация и профиль — УСПЕШНО

1. `POST /api/v1/auth/register`
   - Заголовок: `Content-Type: application/json`
   - Тело: `{"email":"e2e-85ac61abf5eb4d97a383ff686c87305a@example.com","password":"StrongPass123!","name":"E2E User"}`
   - Результат: `201`; возвращены id, email, имя, `registeredAt` и статус `ACTIVE`.
2. `GET /api/v1/users/me`
   - Заголовок: `X-User-Id: 9f3c4e77-3131-428e-9c44-0f7126038c71`
   - Тело: отсутствует.
   - Результат: `200`; профиль совпал с зарегистрированным.

## Сценарий 2. Каталог и два вида пагинации — УСПЕШНО

1. `GET /api/v1/content/categories?page=0&size=10`
   - Заголовки и тело запроса: отсутствуют.
   - Результат: `200`, `X-Total-Count: 1`; получена категория `E2E Science`.
2. `GET /api/v1/content?page=0&size=10`
   - Заголовки и тело запроса: отсутствуют.
   - Результат: `200`, `X-Total-Count: 1`; получен опубликованный материал `E2E Astronomy`.
3. `GET /api/v1/content/feed?page=0&size=10`
   - Заголовок: `X-User-Id: 9f3c4e77-3131-428e-9c44-0f7126038c71`
   - Тело: отсутствует.
   - Результат: `200`, `X-Has-More: false`; общий размер не возвращался.

## Сценарий 3. Настройки пользователя — УСПЕШНО

1. `PUT /api/v1/users/me/settings`
   - Заголовки: `Content-Type: application/json`, `X-User-Id: 9f3c4e77-3131-428e-9c44-0f7126038c71`
   - Тело: `{"language":"ru","timeZone":"Europe/Moscow","favoriteCategories":["fb4a9a0a-2c79-428b-ae88-f49e161b6592"],"dailyGoalMinutes":30,"visibleInSearch":true,"visibleInRating":true,"analyticsConsent":true}`
   - Результат: `200`; сохранённые значения возвращены в ответе.
2. `GET /api/v1/users/me/settings`
   - Заголовок: `X-User-Id: 9f3c4e77-3131-428e-9c44-0f7126038c71`
   - Тело: отсутствует.
   - Результат: `200`; значения совпали с предыдущим запросом.

## Сценарий 4. Подписки — УСПЕШНО

1. `GET /api/v1/subscriptions/plans`
   - Заголовки и тело запроса: отсутствуют.
   - Результат: `200`; получены месячный и годовой тарифы.

## Сценарий 5. Событие аналитики в ClickHouse — УСПЕШНО

1. `POST /api/v1/events`
   - Заголовки: `Content-Type: application/json`, `X-User-Id: 9f3c4e77-3131-428e-9c44-0f7126038c71`
   - Тело: `{"eventId":"5a4fc399-749e-4513-9d55-22d18711a884","userId":"9f3c4e77-3131-428e-9c44-0f7126038c71","sessionId":"e2e-session","eventType":"content_viewed","timestamp":"2026-10-22T09:57:57.178048Z","payload":{"source":"e2e"}}`
   - Результат: `202`; `accepted: true`, id события совпал.

## Сценарий 6. Отсутствие авторизации — УСПЕШНО

1. `GET /api/v1/admin/users?page=0&size=10`
   - `Authorization`, `X-User-Id` и тело отсутствуют.
   - Результат: `200`; ручка доступна без токена и роли, как требуется для ЛР 1.

Во время первого прогона регистрация вернула `500` после записи пользователя из-за
неинициализированного `createdAt`. Исправлено принудительным flush перед построением
ответа и закреплено интеграционным тестом. Повторный прогон всех сценариев успешен.
