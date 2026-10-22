# Соответствие требованиям ЛР 1

Проверено 22 октября 2026 года по требованиям учебного репозитория
[`Discipliny/highload_systems`](https://github.com/Discipliny/highload_systems).

| Требование | Реализация и проверка |
| --- | --- |
| Монолит Spring Boot, Java, Gradle | Один модуль Spring Boot, Java 25, Gradle Wrapper. |
| Feature branches и Conventional Commits | Закреплено в `AGENTS.md` и `docs/development-rules.md`; изменения выполняются в `codex/*`. |
| REST CRUD и корректные статусы | Общий контракт `src/main/resources/static/openapi.yaml`; контроллеры реализуют сгенерированные `*Api` интерфейсы. |
| JPA/JDBC и Flyway | Spring Data JPA и JDBC repositories; схема PostgreSQL создаётся миграциями `db/migration`. |
| Валидация DTO и Entity | Jakarta Validation в OpenAPI моделях и JPA сущностях, бизнес инварианты проверяются сервисами и ограничениями БД. |
| Entity отдельно от DTO | HTTP модели генерируются из OpenAPI; доменные и JPA модели находятся в `model`, внутренние DTO в `dto`. |
| Пагинация не более 50 | Сервисы списков отклоняют `size > 50`; обычные страницы возвращают `X-Total-Count`. |
| Бесконечная прокрутка | `GET /api/v1/content/feed` возвращает `X-Has-More` и `X-Next-Page` без общего количества. |
| Транзакции | Составные сценарии регистрации, оценки, квиза, подписки и модерации помечены `@Transactional`, чтобы изменения нескольких таблиц фиксировались атомарно. |
| Enum как строки | JPA enum поля используют `EnumType.STRING`; JDBC схемы хранят текстовые значения с `CHECK`. |
| Обработка ошибок | `GlobalExceptionHandler` возвращает человекочитаемый `ApiErrorResponse`. |
| Связи сущностей | One-to-many/many-to-one: пользователь и verification tokens; many-to-many: пользователи и приглашения; many-to-many с полем `joined_at`: `competition_participants`. |
| JUnit Jupiter, Testcontainers, покрытие | PostgreSQL и ClickHouse запускаются Testcontainers; JaCoCo останавливает сборку ниже 70%. |
| Docker Compose и env | Приложение, PostgreSQL и ClickHouse запускаются `docker compose`; параметры читаются из переменных окружения. |
| OpenAPI 3 и Swagger UI | Единая спецификация валидируется и генерирует интерфейсы; Swagger UI доступен на `/swagger-ui/index.html`. |

## Авторизация

В ЛР 1 авторизация отсутствует. Токенные маршруты, JWT и проверки ролей удалены.
Для воспроизводимых пользовательских сценариев разрешён необязательный
`X-User-Id`; значение не проверяется и не предоставляет права. Без заголовка
применяется фиксированный лабораторный идентификатор. Полная безопасность
запланирована задачами 8–10 для ЛР 3.

## Проверочная команда

```powershell
.\gradlew.bat clean openApiValidate test jacocoTestCoverageVerification build
```

Интеграционные тесты требуют работающий Docker для контейнеров PostgreSQL и
ClickHouse. Пропущенные тесты считаются ошибкой сборки.
