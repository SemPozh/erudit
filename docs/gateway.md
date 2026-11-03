# Gateway (задача 70)

Spring Cloud Gateway WebFlux — внешняя точка входа ЛР2. Все маршруты используют
`lb://erudit-*`, разрешение адресов выполняют LoadBalancer и Eureka. Путь,
query и необязательный `X-User-Id` передаются без изменения. Авторизация,
JWT, SecurityFilterChain и проверки ролей отсутствуют.

| Префиксы `/api/v1/` | Service ID |
| --- | --- |
| `admin` | erudit-admin |
| `auth`, `users`, `friends` | erudit-user |
| `content`, `media` | erudit-content |
| `assessment`, `program`, `quiz`, `competition`, `rating` | erudit-quiz |
| `subscriptions`, `payments` | erudit-payment |
| `notifications` | erudit-notificator |
| `events`, `analytics` | erudit-event-receiver |

У AI-platform пока нет внешнего API: он вызывается владельцами доменов.
Discovery locator отключён. `/internal/v1/**`, `/eureka/**`, Config Server и
маршруты вида `/{serviceId}/**` наружу не публикуются. Legacy `/hello` не входит
в бизнес-контракт `/api/v1/**` и не переносится. Gateway не создаёт собственных
бизнес-контроллеров; канонический OpenAPI пока остаётся в монолите. Тест проверяет,
что каждому существующему внешнему path соответствует ровно один lb-маршрут.

Ошибки Gateway имеют JSON `{"error":{"code":"NOT_FOUND","message":"Not Found"}}`.
Неизвестный маршрут — 404, отсутствие экземпляров/ошибка подключения/timeout —
503 `SERVICE_UNAVAILABLE`, слишком большое тело — 413 `PAYLOAD_TOO_LARGE`.
Ответы сервисов передаются без преобразования. Технические подробности исключений
не попадают в ответ. Request ID возвращается заголовком даже при ошибке Gateway.

Допустимый `X-Request-ID` — 1–128 символов `[A-Za-z0-9._-]`. Отсутствующий или
некорректный ID заменяется UUID; значение передаётся downstream и Reactor Context.
Один access log содержит ID, метод, путь, статус и длительность. Query,
request/response body и заголовки пользователя в лог не записываются.

Настройки приходят из Config Server:

- `GATEWAY_ALLOWED_ORIGINS`: точный список CORS origins через запятую, по умолчанию
  localhost:3000 и localhost:5173; credentials отключены.
- `GATEWAY_MAX_BODY_BYTES`: по умолчанию 52 MiB, включая multipart overhead для
  существующего лимита файла 50 MiB. Тело буферизуется до этого предела **до**
  отправки upstream; лимит действует и при отсутствии Content-Length. При большом
  числе одновременных загрузок память следует рассчитывать с учётом буферизации.
- Connect timeout 2 секунды, response timeout 5 секунд. Автоматического retry
  изменяющих запросов нет.

```powershell
docker compose up --build -d --wait config-server discovery-server gateway
./scripts/smoke-gateway.ps1
```

Временно Gateway опубликован на `GATEWAY_PORT` (8089), монолит остаётся на 8080
до завершения миграции. Реальные бизнес-сервисы пока являются каркасами, поэтому
их API появятся в последующих задачах. Smoke включает только временный echo
upstream в профиле `gateway-smoke`, регистрирует уникальный экземпляр в Eureka,
проверяет маршрут, заголовки, 404/503, затем удаляет регистрацию и останавливает
echo. У echo нет опубликованного host-порта. Это тестовая фикстура, а не API сервиса.

Проверка: `./gradlew test openApiValidate build`. HTTP-тесты Gateway используют
настоящий Netty listener и HTTP upstream через SimpleDiscoveryClient; Compose
smoke отдельно проверяет именно Eureka. Gateway не имеет БД.
