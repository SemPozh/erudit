# План задач лабораторной работы №2

Источник архитектурных решений: [`lab2-architecture.md`](lab2-architecture.md).
Все задачи находятся в `tasks/todo`, имеют оценку менее 2000 изменённых строк и
должны завершаться отдельным PR.

## Волны выполнения

1. **Каркас:** 67–73 и 91 — multi-project, Config Server, Eureka, Gateway,
   технический starter, Compose, Feign/Resilience4j policy.
2. **Извлечение доменов:** 74–90 — бизнес-сервисы и их собственные базы.
3. **Связи и контракты:** 91–93 — Feign, Resilience4j, OpenAPI aggregation.
4. **Готовность демонстрации:** 94–100 — demo fixtures, тесты, CI, Compose,
   удаление монолита и финальная приёмка.

## Критический путь

`67 → 68/69/72 → 70/73/91 → сервисы 74–90 → 92/93 → 94/95/96/97/98 → 99 → 100`.

Задачи одного уровня без прямой зависимости можно выполнять параллельно.

## Покрытие внешнего API задачами

| Группа путей ЛР1 | Владелец в ЛР2 | Задачи |
| --- | --- | --- |
| `/api/v1/auth/register`, `/verify-email` | user | 74 |
| `/api/v1/users/me`, `/settings`, `/search` | user | 74 |
| `/api/v1/friends/**`, `/users/me/push-tokens/**` | user | 75 |
| `/api/v1/content/categories/**` | content | 76 |
| `/api/v1/content`, `/content/{id}`, `/media/**`, каталог и форматы | content | 77 |
| feed, progress, quiz-cards, reports и content moderation | content + admin facade | 78, 90 |
| `/api/v1/assessment/**`, `/program/**` | quiz | 79 |
| `/api/v1/quiz/**` | quiz | 80, 89 |
| `/api/v1/competition/**`, `/rating/**` | quiz | 81 |
| `/api/v1/events`, `/events/batch` | event-receiver | 82 |
| `/api/v1/analytics/**` | event-receiver | 83 |
| `/api/v1/notifications/**` | notificator | 84, 85 |
| `/api/v1/subscriptions/**` | payment | 86 |
| webhook и mobile receipt | payment | 87 |
| `/api/v1/admin/**` | admin facade | 90 |

Gateway сохраняет эти пути. Новые внутренние маршруты Feign не входят во
внешнюю таблицу и закрыты от клиента.

## Покрытие фоновых процессов

- истечение и обслуживание подписок — задача 86;
- notification triggers, quiet hours, лимиты и retry в mock sink — задача 85;
- доставка событий в ClickHouse и повтор неуспешной записи — задача 83;
- очистка/истечение verification данных пользователя — задача 74;
- реальные брокеры и реальные провайдеры уведомлений остаются за рамками ЛР2.

## Общие условия завершения любой задачи

- авторизация, JWT, роли и Spring Security отсутствуют;
- реальные email, push и SMS не отправляются;
- внешний контракт и DTO соответствуют OpenAPI;
- списки ограничены 50 элементами и имеют согласованную пагинацию;
- новые таблицы создаются Flyway-миграциями своего сервиса;
- unit и интеграционные тесты добавлены по уровню изменения;
- сервис собирается отдельно и в составе корневого build;
- фактический diff задачи не превышает 2000 строк без generated-кода.
