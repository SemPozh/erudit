# Сборка сервисов ЛР2

Корневой проект `:` временно является legacy-монолитом: его `src`, OpenAPI,
Flyway и `build/libs` не перемещаются до задачи 99. Это сохраняет существующий
Docker Compose и позволяет мигрировать домены постепенно.

`infrastructure/` содержит `erudit-config-server`, `erudit-discovery-server` и
`erudit-gateway`; `services/` — восемь бизнес-сервисов из
`lab2-architecture.md`. Пока это независимые запускаемые Spring Boot каркасы
без HTTP API, БД, discovery и бизнес-логики. Их реализация — следующие задачи.
Промежуточные Gradle-проекты `:infrastructure` и `:services` — только каталоги.

## Версии и соглашения

- Java toolchain 25; Spring Boot 4.1.1 и OpenAPI Generator 7.25.0 задаются
  плагинами в корневом `build.gradle.kts`.
- Spring Boot plugin и dependency-management импортируют Boot BOM; Cloud BOM
  2025.1.4 закреплён в `gradle.properties`. Совместимость Boot 4.1.x с Cloud
  2025.1.x начиная с 2025.1.2 указана в
  [официальной матрице](https://spring.io/projects/spring-cloud/).
- `gradle/service-conventions.gradle` задаёт только настройки сборки:
  toolchain, BOM, executable JAR, JUnit Platform, JaCoCo и OpenAPI.
  Runtime/test зависимости объявляются в каждом сервисе локально.
- Общей библиотеки DTO/Entity нет. Сервисы не используют `project(...)`
  зависимости и чужие source sets. Связь между сервисами только через API.
- JaCoCo требует 70% строк прикладного кода; generated OpenAPI и класс
  запуска приложения исключены. У пустого каркаса пока нет прикладного кода.

## Команды

```shell
# Все проекты, включая временный монолит (нужен Docker для Testcontainers)
./gradlew test openApiValidate build

# Один сервис, без сборки монолита и других сервисов
./gradlew :services:erudit-ai-platform:build
./gradlew :services:erudit-ai-platform:bootRun
java -jar services/erudit-ai-platform/build/libs/erudit-ai-platform.jar

# Только legacy, например для существующего Dockerfile
./gradlew :bootJar
docker compose up --build
```

Каркасы используют обычный starter без web-сервера: после успешной
инициализации контекста процесс завершается. HTTP starter, Actuator и
контейнеры сервисов добавляются в соответствующих задачах миграции.

## Добавление сервиса

1. Создать `services/erudit-<name>` (либо `infrastructure/erudit-<name>`),
   добавить полный путь в `settings.gradle.kts`.
2. Скопировать структуру одного из каркасов. В `build.gradle.kts` подключить
   `apply(from = rootProject.file("gradle/service-conventions.gradle"))`.
   Зависимости объявлять через `"implementation"(...)` и
   `"testImplementation"(...)`: соглашения подключаются как script plugin.
3. Задать уникальные Java package и `spring.application.name`.
4. При появлении HTTP API добавить локальный
   `src/main/resources/static/openapi.yaml`. Соглашения автоматически включат
   валидацию в `check`, генерацию интерфейсов в `compileJava` и generated
   source directory. Без контракта OpenAPI-задачи отключены.
   Добавить зависимости, необходимые выбранному MVC/WebFlux генератору,
   локально; реактивный сервис переопределяет опции генерации под WebFlux.
5. Проверить независимые `build`/`bootRun`, затем корневую сборку. Для новой
   бизнес-логики обязательны unit-тесты, для БД/HTTP — интеграционные тесты.

Каждая задача оформляется отдельным PR, менее 2000 изменённых строк без
generated-кода. При приближении к 1800 строкам остаток выделяется в новую
задачу. Сгенерированный код не коммитится. Авторизация и реальные уведомления
в ЛР2 запрещены.
