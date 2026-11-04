# Gateway E2E и CI (№96, 97)

После reset-demo и успешного smoke-demo-contracts:

```powershell
./scripts/smoke-e2e.ps1
./scripts/smoke-resilience.ps1 -Project erudit-demo
```

E2E создаёт временного пользователя, проверяет verify/profile, category/content/
moderation, assessment/program/quiz, competition join/rating, mock subscription,
premium entitlement, inbox/read, events/analytics и audit. Все бизнес-запросы идут
через Gateway; Authorization не передаётся. Method/path/headers/body/status
публикуются в логе, 401/5xx и contract mismatch прекращают прогон. Только локальные
синтетические данные. Отчёты PASS/FAIL: build/gateway-e2e.xml и .xml.md. Проверенный прогон по шагам: [gateway-e2e-results.md](gateway-e2e-results.md).

Resilience ограничен локальным URL и выбранным erudit-demo* project, сверяет
порт Gateway до остановки зависимостей. Останавливает AI, проверяет локальные
cards/quiz и OPEN gauge, затем payment: предварительно активный premium маскируется,
бесплатный контент доступен. В finally зависимости восстанавливаются. После recovery
проверяются AI output/CLOSED gauge и возврат premium entitlement. Docker используется
только для orchestration/Actuator diagnostics, без SQL/psql и прямых business URLs.
Отчёт: build/gateway-resilience.md. Перед повторением используйте demo reset.

После restart готовность payment в Gateway ещё не означает готовность Feign-клиента
Content: у них независимые discovery caches и circuit state. Recovery ограниченно
ожидает снятия premium-маски (до 180 секунд), проверяя контракт каждого ответа.
401/5xx и schema mismatch не повторяются и сразу завершают smoke ошибкой.

CI path plan консервативен: root всегда запускается, shared/build/config/scripts/
unknown changes тестируют все модули; доменные изменения расширяются на callers и
Gateway. Четыре unit-теста защищают impact graph. Root check никогда не фильтруется.
Gradle cache сохраняется через setup-gradle и BuildKit (locked dependency cache).

`build` собирает все модули и валидирует OpenAPI. `system-smoke` собирает все
11 application images, проверяет Git SHA/UTC/content digest/UID 10001, выполняет
contract/E2E/resilience и normal-profile restart smoke. Compose default/demo/debug
проверяются отдельно. Артефакты: JUnit/HTML/JaCoCo, API results и images-report.json.
CI не публикует образы и не использует registry/provider credentials.

Стабильный aggregate check `test` требует успеха plan, selected module tests, build,
infrastructure и system smoke. В GitHub ruleset для main этот check должен быть
Required status check; workflow не изменяет repository permissions/rulesets и не
может запретить owner bypass без настройки владельцем репозитория.

Локальная сборка: `./scripts/build-images.ps1 -Project erudit-demo`. Чистый CI checkout
помечается clean, локальные незакоммиченные изменения — dirty. Прямой compose build
без args имеет явную local/worktree provenance, а не ложный release SHA. Content
digest всегда неизменяем; Git/UTC labels задаются проверяемым builder.

Проверка GitHub API 5 ноября 2026 подтвердила protected main и обязательные
`test`/`build` с enforcement_level=everyone. Таким образом последний критерий 97
подтверждён, хотя UI repository options по-прежнему недоступен текущему аккаунту.
Снимок: [history/main-protection.json](history/main-protection.json).
Полная приёмка и демонстрация: [lab2-acceptance.md](lab2-acceptance.md).
