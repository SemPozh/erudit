# Архитектура ERUDIT для лабораторной работы №2

## Принятое решение

Монолит ЛР1 декомпозируется в Gradle multi-project repository. Внешние пути
`/api/v1/**` сохраняются и доступны только через `erudit-gateway`. Каждый
бизнес-сервис владеет своей схемой и Flyway-миграциями; прямой доступ к чужим
таблицам и общие доменные Entity запрещены.

Авторизации, JWT, Spring Security и проверки ролей в ЛР2 нет. Необязательный
`X-User-Id` остаётся непроверяемым идентификатором демонстрационного контекста.

Реальная отправка email, push и SMS запрещена до ЛР4. Нотификатор сохраняет
уведомления и попытки доставки, а провайдером служит локальный mock sink.

## Состав системы и владельцы

| Приложение | Владелец | Ответственность | Хранилище |
| --- | --- | --- | --- |
| `erudit-gateway` | Илья | маршрутизация, CORS, request ID, общий Swagger | нет |
| `erudit-config-server` | Илья | внешняя конфигурация всех приложений | Git/native config repository |
| `erudit-discovery-server` | Илья | Eureka registry | нет |
| `erudit-admin` | Дестини | административный фасад и аудит | PostgreSQL `erudit_admin` |
| `erudit-user` | Дестини | регистрация, профиль, настройки, друзья, push-токены | PostgreSQL `erudit_user` |
| `erudit-content` | Семен | категории, контент, медиа, прогресс, жалобы, модерация | PostgreSQL `erudit_content` |
| `erudit-quiz` | Дестини | assessment, program, quiz, competition, rating | PostgreSQL `erudit_quiz` |
| `erudit-event-receiver` | Семен | приём событий и аналитика | PostgreSQL R2DBC + ClickHouse |
| `erudit-ai-platform` | Илья | mock AI и адаптер будущего провайдера | без обязательной БД |
| `erudit-payment` | Илья | тарифы, подписки, платежи, entitlement | PostgreSQL `erudit_payment` |
| `erudit-notificator` | Семен | inbox, настройки, шаблоны, mock delivery | PostgreSQL `erudit_notification` |

`erudit-admin` не читает чужие базы. Он координирует операции через Feign и
хранит только аудит. `erudit-ai-platform` возвращает результат генерации, а
владелец домена сохраняет результат.

Обычные реляционные сервисы используют Spring Data JPA или Spring Data JDBC,
а не собственный слой SQL поверх общей базы. Выбор фиксируется внутри сервиса и
не влияет на его HTTP-контракт.

## Реактивные сервисы

- `erudit-event-receiver`: Spring WebFlux, Reactor и Spring Data R2DBC.
  R2DBC хранит квитанции и идемпотентность. Блокирующий ClickHouse JDBC
  изолируется на выделенном `boundedElastic` scheduler.
- `erudit-notificator`: Spring WebFlux, Reactor и Spring Data JDBC. Блокирующий
  JDBC выполняется через выделенный scheduler с ограниченным пулом. Это явно
  документированный учебный компромисс для требования Reactor + JDBC.

Event loop нельзя блокировать JDBC, файловыми операциями или Feign-вызовами.

## Синхронные взаимодействия

| Клиент | Сервер | Назначение | Fallback |
| --- | --- | --- | --- |
| admin | user | поиск и блокировка пользователя | понятная 503 |
| admin | content | модерация и жалобы | понятная 503 |
| admin | notificator | массовая постановка в mock delivery | понятная 503 |
| admin | payment | просмотр платежных данных | понятная 503 |
| content | payment | premium entitlement | `premium=false` |
| content | ai-platform | quiz cards | локальная генерация |
| quiz | content | сведения о контенте | понятная 503 |
| quiz | ai-platform | генерация вопросов | локальная генерация |
| notificator | user | профиль и локальные адресаты | повтор задачи позже |
| notificator | event-receiver | событие доставки | best effort |
| payment | user | проверка существования пользователя | понятная 503 |

Все вызовы выполняются через Spring Cloud OpenFeign с discovery по service ID.
Circuit Breaker реализуется Resilience4j. Retry разрешён только для
идемпотентных операций. Таймауты обязательны и приходят из Config Server.

## API и демонстрационные данные

Каждый сервис хранит собственную OpenAPI-спецификацию. Gateway публикует общий
Swagger UI и ссылки на спецификации сервисов. Внешние пути остаются совместимыми
с ЛР1; внутренние Feign API используют `/internal/v1/**` и не маршрутизируются
наружу.

Docker Compose включает профиль `demo`. Он создаёт детерминированные категории,
пользователей, контент, квизы, соревнования, тарифы, шаблоны и записи для
модерации. OpenAPI содержит рабочие примеры с этими ID и описывает порядок
составных сценариев. Любую ручку можно вызвать из Swagger без SQL и ручного
редактирования БД. Для изменяемых сценариев предусмотрен скрипт сброса demo
окружения и повторяемый gateway smoke test.

## Конфигурация, наблюдаемость и запуск

- Config Server является источником общих таймаутов, URL хранилищ, лимитов и
  feature flags; секреты поступают только из переменных окружения.
- Все приложения регистрируются в Eureka и имеют Actuator readiness/liveness.
- `X-Request-ID` проходит через Gateway, Feign и логи всех сервисов.
- `docker compose up --build` поднимает инфраструктуру и все приложения с
  health-based dependencies.
- CI валидирует все OpenAPI, запускает unit/integration/contract tests,
  проверяет покрытие и собирает каждый Docker image.

## Ограничение размера задач

Каждая задача ЛР2 оценивается менее чем в 2000 изменённых строк, включая код,
конфигурацию и тесты. Если фактический diff приближается к 1800 строкам,
исполнитель обязан вынести оставшуюся независимую часть в новую задачу до PR.
Сгенерированный OpenAPI-код в оценку не включается, но не коммитится.
