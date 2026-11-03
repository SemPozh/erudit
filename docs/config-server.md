# Config Server (задача 68)

`erudit-config-server` использует Spring Cloud Config с файловым backend
`native`. Собственных контроллеров и бизнес-маршрутов нет: стандартный
инфраструктурный API Config Server обслуживает `/{application}/{profile}`.
Его нельзя маршрутизировать через публичный Gateway.

## Запуск

```shell
./gradlew :infrastructure:erudit-config-server:bootRun
# В другом терминале:
./gradlew :services:erudit-ai-platform:bootRun
```

По умолчанию сервер читает `config-repository` из корня репозитория и слушает
порт 8888. Для запуска JAR из другой директории задайте
`CONFIG_REPOSITORY_LOCATION` абсолютным file URI, например
`file:///C:/ITMO/4/7/vps/erudit/config-repository/`.

```shell
docker compose up --build -d config-server
curl http://localhost:8888/actuator/health
curl http://localhost:8888/erudit-user/default
curl http://localhost:8888/erudit-user/demo
```

Compose монтирует репозиторий read-only и публикует порт только на loopback.
Проверка health включает доступность репозитория, probes доступны на
`/actuator/health/liveness` и `/actuator/health/readiness`.
У будущих Compose-клиентов должна быть зависимость
`config-server: {condition: service_healthy}` и
`CONFIG_SERVER_URL=http://config-server:8888`.

## Настройки

`application.properties` содержит общие лимиты пагинации (максимум 50),
Feign connect/read timeout, Actuator и адрес Eureka. Файл с именем сервиса
задаёт порт и его собственное хранилище; `application-demo.properties`
включает флаг demo. Само создание демонстрационных данных — отдельная задача.
Параметры БД пока не подключают JDBC/R2DBC: зависимости добавляются при
миграции соответствующего сервиса.

Пароли и имена пользователей сохраняются только как `${DB_PASSWORD}`,
`${DB_USERNAME}`, `${CLICKHOUSE_PASSWORD}` и `${CLICKHOUSE_USERNAME}`.
Их задают в окружении **клиента**, не Config Server. У паролей нет значения
по умолчанию. URL БД переопределяются `DB_URL` / `R2DBC_URL`, адрес Eureka —
`EUREKA_URL`. Параметры чужих БД не используются.

Все десять клиентов используют обязательный `spring.config.import` без
`optional:`, fail-fast и шесть попыток с backoff 1–5 секунд. Bootstrap-настройки
retry остаются локальными: они должны работать до получения конфигурации.
Config Server не является собственным клиентом. Монолит сохраняет прежнюю
конфигурацию до завершения миграции.

После изменения файла перезапустите клиент — конфигурация будет загружена
заново. Уже запущенный клиент сохраняет прежние значения. Refresh endpoint,
Bus и брокеры здесь не добавляются. Это соответствует поведению
[Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html)
и [native backend](https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/file-system-backend.html).

## Проверки

```shell
./gradlew :infrastructure:erudit-config-server:build
./gradlew test openApiValidate build
docker compose config --quiet
```

HTTP-тесты поднимают настоящий Config Server на случайном порту без БД:
проверяют все 11 имён приложений с профилями default/demo, health/probes и
placeholder-значения вместо секретов. Клиентские тесты проверяют повторное
чтение изменённого файла после перезапуска и ровно три попытки при HTTP 503
(сокращённые интервалы только в тесте). Testcontainers нужен тестам монолита;
у Config Server собственного хранилища PostgreSQL нет.
