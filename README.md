# ERUDIT — ЛР2

Восемь бизнес-сервисов, Gateway, Config Server, Eureka, семь PostgreSQL и ClickHouse.
Root Gradle — агрегатор и архитектурные/contract tests, без приложения, datasource
и монолитного Docker image. Модули перечислены в `settings.gradle.kts`.

```powershell
./gradlew test openApiValidate build
./scripts/reset-demo.ps1
./scripts/smoke-demo-contracts.ps1
./scripts/smoke-e2e.ps1
./scripts/smoke-resilience.ps1
```

Java 25, Docker Compose v2 и PowerShell 7.5+. Обычное окружение:
`docker compose up --build -d --wait`, Gateway/Swagger на порту 8089.
Для метаданных локальных образов используйте `scripts/build-images.ps1`:
он фиксирует Git SHA, UTC, dirty-state и проверяет UID 10001 всех 11 приложений.
После сборки: `docker compose up -d --no-build --wait`.

Demo — отдельный проект erudit-demo на порту 18089; reset разрешён только этому
семейству имён и не удаляет данные обычного проекта. Swagger:
http://localhost:18089/swagger-ui/index.html. Секреты окружения задаются через
локальный `.env` (не коммитить), пример — `.env.example`.

- [Demo fixtures и stateful сценарии](docs/demo-runbook.md)
- [Архитектура и владельцы](docs/lab2-architecture.md)
- [Gateway E2E, resilience и CI gate](docs/system-gates.md)
- [Удаление legacy, соответствие маршрутов и история](docs/legacy-retirement.md)
- [Изоляция баз и baseline](docs/database-isolation.md)
- [Gateway Swagger](docs/gateway-swagger.md)

Авторизации/JWT/проверок ролей в ЛР2 нет. X-User-Id выбирает непроверяемый
контекст; AI/payment/notificator работают локально, email/push/SMS не отправляются.
Сервисные OpenAPI в `services/erudit-*/src/main/resources/static/openapi.yaml` —
источник внешнего контракта. `docs/api/public-operations.json` фиксирует утверждённые
83 method/path/operationId. Изменение списка требует явного обновления baseline.

История ЛР1 сохранена в Git и `docs/history/legacy-inventory.json`;
старые aliases уже не входили в сервисные контракты после №93. Исторические
документы ЛР1 не являются инструкцией запуска текущей системы.
