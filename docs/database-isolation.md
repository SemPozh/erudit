# Базы и миграции ЛР2

Каждый PostgreSQL-домен имеет отдельный контейнер, database, login и
`flyway_schema_history`. HTTP/Feign передаёт UUID чужого домена, без foreign key
в чужие таблицы и без импорта чужих Entity/repository. AI и Gateway БД не имеют.

| Сервис | Database / приложение по умолчанию | Миграции сервиса |
| --- | --- | --- |
| User | erudit_user | V1 users, V2 friends/devices, R admin restrictions |
| Content | erudit_content | V1 categories, V2 content/media, V3 workflows |
| Quiz | erudit_quiz | V1 assessment, V2 quizzes, V3 competitions |
| Event receiver | erudit_event | V1 event receipts |
| Notificator | erudit_notification | V1 inbox/templates, V2 mock delivery |
| Payment | erudit_payment | V1 subscriptions/payments, V2 provider events |
| Admin | erudit_admin | V1 audit |

Файлы находятся в `services/erudit-*/src/main/resources/db/migration`.
Legacy migrations удалены из runtime в №99; история доступна через commit 56538b3
и `docs/history/legacy-inventory.json`. Их history и номера нельзя переносить в
service database: все сервисы начинают собственную историю с V1. Применённые
версии не редактируются, дальнейшие изменения идут новыми миграциями.

## Роли и права

`docker/postgres/init-domain.sh` применяется официальным PostgreSQL entrypoint
только на новом томе. `erudit_bootstrap` используется для bootstrap и не передаётся
приложению. Его пароль задаётся отдельным `<DOMAIN>_DB_OWNER_PASSWORD`.
`<DOMAIN>_DB_USER/PASSWORD` задают обычный login приложения: NOSUPERUSER,
NOCREATEDB, NOCREATEROLE, NOREPLICATION. PUBLIC лишён доступа к domain database,
public schema и connect к postgres/template1. Приложению выданы только CONNECT
к своей БД и USAGE/CREATE на public для локального Flyway. Созданные им таблицы
принадлежат ему. Runtime и Flyway используют одну роль в пределах своего домена;
разделение runtime/migrator внутри домена пока не требуется.

Общие пароли или соединение приложения под bootstrap запрещены. Настройки
Compose передают каждому приложению только свои DB credentials. Payment больше
не требует запущенного User для старта: ошибки бизнес-вызовов обрабатывает Feign.

## Существующие данные

Обычный `compose up` не меняет пользователей существующего PostgreSQL-тома.
Перед переходом сохраните резервную копию. Для томов прежней версии, где
приложение было superuser, сначала подключитесь локально под прежним владельцем
и создайте bootstrap login с выбранным owner-паролем. Затем под bootstrap:

```sql
-- Пример для User; имена/пароль должны соответствовать выбранному окружению.
CREATE ROLE erudit_bootstrap LOGIN SUPERUSER PASSWORD 'replace-with-owner-password';
-- Последующие команды выполнить после переподключения под erudit_bootstrap.
ALTER DATABASE erudit_user OWNER TO erudit_bootstrap;
ALTER SCHEMA public OWNER TO erudit_bootstrap;
REVOKE ALL ON DATABASE erudit_user FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE CONNECT ON DATABASE postgres, template1 FROM PUBLIC;
GRANT CONNECT ON DATABASE erudit_user TO erudit_user;
GRANT USAGE, CREATE ON SCHEMA public TO erudit_user;
ALTER ROLE erudit_user NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
```

Выполните аналогично только в соответствующем контейнере каждого домена.
Таблицы и существующая Flyway history сохраняются. Init script повторно на
существующем томе не запускайте: он предназначен для нового login. Для полностью
одноразовых локальных данных можно создать новый Compose project; удалять тома
рабочего окружения для этого не требуется.

Event Receiver раньше использовал `schema.sql`. Теперь Flyway подключается по
`DB_URL` через JDBC только на этапе запуска, а обработка запросов остаётся R2DBC.
Для его собственной старой схемы baseline-on-migrate разрешён с версией 0: V1 с
`CREATE TABLE IF NOT EXISTS` регистрируется без удаления квитанций. Для остальных
доменов baseline-on-migrate выключен: непустая чужая/legacy схема вызывает отказ,
а не молчаливое принятие. Схема Event должна быть именно прежним event store;
совместимость колонок следует проверить перед обновлением. Flyway блокирует
готовность repository до завершения инициализации.

## Проверка

`./gradlew test openApiValidate build` включает:

- `DatabaseIsolationTest`: все семь наборов миграций на отдельных databases и
  ролях PostgreSQL Testcontainer; повторный migrate без изменений, проверка history,
  запрет superuser/createdb/createrole и connect к чужим БД; сохранение старой квитанции.
- `DataOwnershipArchitectureTest`: запрет cross-service Java references/Gradle
  dependencies, внешних FK и datasource defaults чужих доменов.
- Event store tests используют Flyway вместо ручного выполнения schema.sql.

`./scripts/smoke-database-isolation.ps1` создаёт уникальный одноразовый Compose
project, собирает образы и по очереди поднимает каждый из семи сервисов только со
своим PostgreSQL (Event также со своим ClickHouse), Config Server и Eureka.
Проверяются healthy, права login и локальная Flyway history; другие бизнес-БД в
этом проекте выключены. В конце удаляются только контейнеры/тома созданного тестового
проекта. Сети также имеют project-specific имена: фиксированный общий DNS alias мог бы
направить JDBC к контейнеру чужого Compose project. Основной Compose project не останавливается. Для отдельного домена:
`./scripts/smoke-database-isolation.ps1 -Domains event`.
