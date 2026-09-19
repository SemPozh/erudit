# ERUDIT

## Описание

Проект - backend приложения для развития эрудированности. Первая версия монолитная.

## Правила работы с репозиторием

Используем подход Trunk-based development. Ветка main всегда содержит рабочий код, которй готов к релизу в продакшн.
Для разработки фичи отводим от main отдельную ветку. После завершения работы над фичей создаем PR в main.
После ревью и прохождения всех тестов в CI, пулл реквест мержится в main.

Перед началом разработки нужно подтянуть актуальный main: git pull origin main

## Локальный запуск

`docker compose up --build` запускает приложение и PostgreSQL. По умолчанию база
`erudit` доступна приложению через сервис `postgres`; Flyway применяет миграции
при старте. Для своих учётных данных задайте `POSTGRES_DB`, `POSTGRES_USER` и
`POSTGRES_PASSWORD` в окружении или в локальном `.env` (файл не коммитить).

Compose также запускает ClickHouse. Скрипт
`src/main/resources/clickhouse/events.sql` создаёт таблицу событий при первом
запуске контейнера; приложение применяет тот же идемпотентный скрипт при старте.
Настройки подключения: `CLICKHOUSE_URL`, `CLICKHOUSE_USERNAME`,
`CLICKHOUSE_PASSWORD` для приложения вне Docker; `CLICKHOUSE_DB`,
`CLICKHOUSE_USER`, `CLICKHOUSE_PASSWORD` для Compose. Тестовый профиль отключает
подключение к ClickHouse; smoke-тест записи и чтения запускается в CI на
отдельном сервисе ClickHouse.

При запуске приложения вне Docker задайте `DB_URL`, `DB_USERNAME` и `DB_PASSWORD`.
Тесты используют профиль `test` и базу H2 в памяти: `./gradlew test`.

## OpenAPI и Swagger UI

Единый контракт API находится в `src/main/resources/static/openapi.yaml`.
`./gradlew openApiValidate` проверяет его, а при сборке Gradle генерирует
Java-интерфейсы API и модели ответов в `build/generated/openapi`. Контроллеры
реализуют эти интерфейсы; сгенерированные файлы не редактируются вручную.

После `docker compose up --build` откройте
`http://localhost:8080/swagger-ui/index.html`. UI читает исходный контракт по
`http://localhost:8080/openapi.yaml` и позволяет выполнить запросы через
**Try it out**. `/hello` работает сразу. Для успешной жалобы сначала нужна
запись контента в PostgreSQL; иначе `/api/v1/content/{id}/report` вернёт 404.
