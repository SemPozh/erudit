# Общий Swagger Gateway (ЛР2)

Откройте `http://localhost:8089/swagger-ui/index.html` после `docker compose up --build`.
Порт задаётся `GATEWAY_PORT`. В Select a definition доступны все восемь
бизнес-модулей из `services/`: Admin, AI, Content, Event receiver, Notificator,
Payment, Quiz и User. Упоминание девяти сервисов в карточке №71 не соответствует
составу репозитория и таблице архитектуры.

AI имеет только internal API, поэтому его публичная спецификация содержит пустой
`paths`. Его Feign endpoints не публикуются через Gateway.

`/openapi/swagger-config` возвращает список определений, `/openapi/erudit-user`
(и соответствующие имена остальных модулей) — публичную JSON-спецификацию.
Контракты включаются в Gateway jar при сборке из service-owned OpenAPI той же
ревизии. Поэтому Swagger доступен даже при недоступном бизнес-сервисе, а обновление
контракта требует пересборки Gateway. Блокирующих сетевых чтений на event loop нет.

Проекция оставляет `/api/v1/**`, используемые tags с их описаниями и только
транзитивно достижимые components. Internal paths, их DTO и схемы авторизации
отсутствуют. `servers` на всех уровнях указывает `/`, чтобы Try it out выполнялся
на текущем origin Gateway. Авторизация не нужна; `X-User-Id` — непроверяемый контекст
ЛР2, реальные уведомления не отправляются.

Gateway при старте отклоняет дубли внешнего path и operationId (включая inline
YAML), отсутствующие operationId и неразрешимые ссылки components. HTTP-тесты
проверяют список всех модулей, UI/initializer, origin запросов и отсутствие private
операций. Unit-тесты проверяют дубли и транзитивное удаление внутренних DTO.

Проверка: `./gradlew test openApiValidate build`, затем `./scripts/smoke-swagger.ps1`.
Для ручной проверки выберите erudit-user, раскройте GET /api/v1/users, нажмите
Try it out и Execute: Request URL должен начинаться с текущего адреса Gateway.
Запуск бизнес-сервисов нужен для исполнения операций, но не просмотра контрактов.
