# Eureka Discovery (задача 69)

Eureka Server получает конфигурацию из Config Server и слушает порт 8761.
Он не регистрирует себя и не получает реестр от самого себя. Собственных
бизнес-контроллеров нет: используется стандартный API Eureka `/eureka/apps`.
Инфраструктурные порты опубликованы Compose только на loopback; публичный
Gateway не должен маршрутизировать Eureka (задача 70).

Все приложения имеют Eureka Client и Actuator. Service ID определяется
`spring.application.name`, instance ID включает имя и уникальный идентификатор
экземпляра. Health URL указывает на `/actuator/health`, status URL — на
`/actuator/info`. При штатной остановке клиент снимает регистрацию.
Глобальные параметры находятся в `config-repository/application.properties`.
Config Server хранит bootstrap-параметры discovery локально, поскольку не
может получать собственную конфигурацию через себя. Его health не зависит
от Eureka: это предотвращает цикл готовности Config → Eureka → Config.

Порядок запуска: Config Server → Eureka → клиенты. Внутри Compose клиенты
используют `EUREKA_URL=http://discovery-server:8761/eureka/`. Адреса самих
сервисов задавать вручную не нужно: каждый сообщает свой host/port реестру.
По умолчанию Eureka сохраняет self-preservation; тест не отключает эту
защиту в рабочей конфигурации.

```powershell
docker compose up --build -d config-server discovery-server
./scripts/smoke-discovery.ps1
```

Smoke включает профиль `discovery-smoke`, запускает настоящие Gateway и
AI-platform, ждёт их UP-регистрации через JSON API, проверяет health URL,
останавливает их и ждёт исчезновения из реестра. HTML dashboard не используется.
После проверки Config Server и Eureka остаются запущены; два smoke-клиента
останавливаются. Профиль не запускает монолит или БД.

Для локального запуска без Compose сначала запустить Config Server, затем
`:infrastructure:erudit-discovery-server:bootRun`, затем нужные клиенты.
HTTP-каркасы теперь остаются запущенными. Gateway, event-receiver и notificator
используют WebFlux; остальные сервисы — MVC. Регистрация не добавляет
авторизацию или реальные уведомления.

```shell
./gradlew :infrastructure:erudit-discovery-server:build
./gradlew test openApiValidate build
```

Интеграционный тест поднимает настоящий Eureka Server на случайном порту,
проверяет регистрацию, lookup, health metadata, удаление и отсутствие
саморегистрации. Полный lifecycle настоящих приложений проверяется smoke.
У Eureka нет собственной PostgreSQL, поэтому её HTTP-тест не требует БД.

Настройки соответствуют [Spring Cloud Netflix](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html).
