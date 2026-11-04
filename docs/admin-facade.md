# Admin facade (ЛР2, задача 90)

Admin service владеет только PostgreSQL erudit_admin и таблицей admin_audit.
Данных или Entity чужих доменов в admin module нет. Все операции над User,
Content, Notification и Payment выполняются через типизированные Feign clients
с discovery по service ID. Config Server задаёт timeout; автоматические повторы
Feign/LoadBalancer выключены, в том числе для записывающих операций.

## API и учебный режим

Все `/api/v1/admin/**` теперь маршрутизируются Gateway в erudit-admin. Swagger
явно указывает: авторизации и роли ADMIN нет. X-User-Id является только
непроверяемым actor ID, по умолчанию demo UUID ...0001.

Сохранены users, users/{id}/block, content/moderation, content/{id}/approve,
content/{id}/reject, content/reports, content/reports/{id}/resolve,
notifications/send-many и audit. Добавлены GET users/{id}/subscription и
users/{id}/payments: это read-only вызовы существующих Payment API внутри
приватной сети с X-User-Id выбранного пользователя. Пагинация ограничена 50;
страничные ответы имеют X-Total-Count.

User service предоставляет собственные internal search/block endpoints.
Ограничение пользователя хранится только в его БД; повтор block идемпотентен,
а общий user lookup возвращает active=false. Это бизнес-статус, не новая
авторизация. Независимая идемпотентная R-миграция создаёт user_restrictions,
не занимая V2, выделенную параллельной задаче друзей и устройств.

Content internal moderation возвращает дополнительное data с материалом;
лёгкий общий DTO contentId/status/reason остаётся совместим. Внутренние пути
не публикуются через Gateway. Блокировка или модерация не читают чужую БД.

Send-many использует templateId и parameters, затем ставит по одному сообщению
в существующий локальный mock enqueue Notificator. Дубликаты userIds удаляются;
допускается 1..1000 получателей. Отсутствующие параметры шаблона и отключённый
шаблон отклоняются до постановки сообщений. Настоящие провайдеры не вызываются.

## Результаты и аудит

До обращения к зависимости Admin фиксирует RUNNING в отдельной локальной
транзакции. Затем сохраняет SUCCESS, FAILED, PARTIAL или UNKNOWN. Сбой удалённого
сервиса не откатывает локальный аудит. X-Request-ID проходит в каждый Feign
request и хранится вместе с actor, action и target. В HTTP audit response
metadata содержит status, requestId и сведения о результате.

При частичной отправке ответ — 503 с error.code=PARTIAL_FAILURE и details.sent,
failed/requested. Подтверждённые постановки не откатываются. При timeout или 5xx
результат отдельной операции может быть неизвестен: аудит UNKNOWN и ответ
DEPENDENCY_UNAVAILABLE не утверждают, что удалённая запись не произошла.
При сбое очереди failed означает неподтверждённых получателей; повторять весь
batch автоматически нельзя. Audit endpoint поддерживает actor/action/time filters.
Чтение самого аудита не порождает новых записей, сохраняя стабильность страниц.

Распределённых транзакций нет. Если запись завершения аудита недоступна после
удалённой операции, ранее сохранённый RUNNING остаётся для разбора. Блокировка
не зависит от текущей роли: Spring Security/JWT по-прежнему относятся к ЛР3.

Сервисный OpenAPI Admin включает Payment views. Временный root adapter удалён
в №99; фасад использует только Feign и собственный аудит.

## Проверка

```powershell
.\gradlew.bat test openApiValidate build
docker compose up --build -d --wait admin-service user-service content-service notificator gateway
.\scripts\smoke-admin.ps1
```

Admin запускается только с собственной БД/Config/Discovery и остаётся доступным
при отказе доменных сервисов. После холодного старта smoke ждёт discovery, затем
создаёт свои user/content fixtures, проходит административные операции и удаляет
созданного пользователя; материал остаётся архивным, audit и mock inbox сохраняются.
Тесты используют JDK HTTP stubs для четырёх зависимостей и свой PostgreSQL через
Testcontainers: success, partial failure, timeout, malformed response, отсутствие
повторов записи, actor/request ID и фильтры. User integration test проверяет
собственную таблицу ограничений и active=false без доступа к другой БД.
