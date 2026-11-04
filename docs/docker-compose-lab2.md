> Исторический документ до №99. Актуальные команды: [README](../README.md),
> [system gates](system-gates.md). Старые пути восстанавливаются из commit 56538b3.

# Docker Compose для инфраструктуры ЛР2

Базовый профиль запускает Config Server, Eureka и Gateway. Gateway подключён к
сетям `edge` и `internal`; Config Server и Eureka доступны сервисам только через
`internal` и не публикуют host-порты. Smoke проверяет их readiness из самих
контейнеров, не нарушая изоляцию сети.

```powershell
Copy-Item .env.example .env
docker compose up --build --detach --wait
.\scripts\smoke-infrastructure.ps1
```

Порядок готовности задаётся `depends_on.condition: service_healthy`:
Config Server → Eureka → Gateway. У инфраструктурных контейнеров настроены
`restart: unless-stopped`, graceful shutdown и healthchecks. Smoke перезапускает
Eureka и Gateway и проверяет их восстановление без `sleep` в Compose.
Тот же сценарий выполняет обязательная CI job `infrastructure-smoke` на Docker
Engine GitHub runner.

Монолит и его PostgreSQL/ClickHouse временно сохранены для переходного периода,
но вынесены в профиль `legacy` и не запускаются вместе с инфраструктурой по
умолчанию:

```powershell
docker compose --profile legacy up --build --detach --wait
```

Порты будущих бизнес-сервисов наружу не публикуются. Для инфраструктурных smoke
используются профили `gateway-smoke` и `discovery-smoke`; тестовые upstream также
не имеют host-портов. Финальный состав баз и сервисов добавляется задачей 98.

`.env.example` содержит только локальные значения и placeholders. Файл `.env`
не коммитится. Секреты рабочего окружения передаются переменными среды.
