# Технический starter (задача 72)

`platform:erudit-observability` — обычный Java library JAR с Boot auto-configuration.
Он подключён к 11 приложениям ЛР2. Монолит сохраняет собственные соглашения до
задачи 99. В starter нет Entity, бизнес DTO, репозиториев, business exceptions
или зависимостей JDBC/JPA/R2DBC. Единственный DTO — техническая ошибка.
`verifyNoDatabaseDependencies` проверяет runtime classpath при каждом `check`;
Hibernate Validator разрешён, поскольку это Bean Validation, а не ORM.

## Ошибки и request ID

MVC advice и WebFlux error handler возвращают одинаковый envelope:

```json
{"error":{"code":"VALIDATION_ERROR","message":"Bad Request"}}
```

Validation — 400 `VALIDATION_ERROR`, некорректный JSON — 400 `MALFORMED_JSON`,
неизвестный путь — 404 `NOT_FOUND`, неожиданный сбой — 500 `INTERNAL_ERROR`.
Текст исключений и rejected values не раскрываются. Бизнес-обработчики остаются
в сервисах; специализированный `ErrorWebExceptionHandler` (например, Gateway)
отключает общий reactive handler. MVC business advice следует задавать более
высокий приоритет, чем стандартный `LOWEST_PRECEDENCE`.

`X-Request-ID` нормализуется по правилу `[A-Za-z0-9._-]{1,128}` либо генерируется
как UUID. Он доступен в request/response и `MDC.requestId`; в WebFlux — также
в Reactor Context под ключом `X-Request-ID`. Micrometer ContextRegistry и Boot
automatic context propagation восстанавливают MDC при переключении scheduler,
не оставляя значение на потоке другого запроса. Servlet filter восстанавливает
предыдущий MDC даже при исключении. Gateway использует этот же reactive filter
вместо своей копии и сохраняет access log без тела, query и пользовательских
заголовков. MVC filter обслуживает синхронную servlet-dispatch обработку;
произвольные пользовательские executors должны переносить контекст явно.

## Наблюдаемость

Низкоприоритетные defaults задаёт EnvironmentPostProcessor; настройки приложения,
Config Server и окружения имеют приоритет:

- JSON ECS logs (`logging.structured.format.console=ecs`), включая requestId,
  traceId и spanId из MDC.
- Actuator `health,info,prometheus`, readiness/liveness probes.
- Brave/Micrometer tracing, W3C trace propagation, sampling 0.1 по умолчанию.
- `spring.reactor.context-propagation=auto`.
- Zipkin export выключен. Для включения задайте `ZIPKIN_ENABLED=true`,
  `ZIPKIN_ENDPOINT` и при необходимости `TRACING_SAMPLING_PROBABILITY`.

Для исходящих HTTP-вызовов используйте внедрённые Boot `RestClient.Builder` /
`WebClient.Builder`, а не созданные вручную клиенты: только настроенные builders
автоматически передают trace context. Request ID для Feign будет добавлен в
соответствующей задаче межсервисных вызовов; это не механизм авторизации.

Starter не подключает web server или вторую web-stack транзитивно: MVC/WebFlux
зависимости compileOnly, приложение выбирает свой starter самостоятельно.
Это единственная допустимая общая runtime-библиотека технических соглашений;
бизнес-сервисы не импортируют исходники или модели друг друга.

## Проверка

```shell
./gradlew :platform:erudit-observability:build
./gradlew test openApiValidate build
```

Тестовые приложения запускаются на настоящих HTTP-портах в MVC и WebFlux режимах.
Проверяются одинаковые validation/malformed JSON/404/500 ответы, request ID,
MDC после scheduler switch, JSON logs, входящий и исходящий trace context,
Prometheus и probes. Отдельный тест проверяет восстановление MDC после исключения.
