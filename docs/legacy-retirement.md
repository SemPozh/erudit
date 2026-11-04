# Удаление legacy (№99)

Источник аудита: commit `56538b3`. Список всех старых controllers/services/jobs/
models/migrations и владельцев: `history/legacy-inventory.json`. Восстановление
любого файла для аудита: `git show 56538b3:<legacy-path>`.

Legacy имел 87 операций, текущие утверждённые service-owned спецификации — 83.
46 method/path сохранены буквально. Остальные адреса были заменены либо исключены
при декомпозиции и принятии №93; этот PR не объявляет побайтовую совместимость с ЛР1
и не возвращает старые aliases. При удалении root текущий Gateway API не меняется:
его 83 операции зафиксированы в `api/public-operations.json` и проверяются тестом.

| Старый домен / адреса | Текущий владелец и контракт |
| --- | --- |
| auth/register, verify-email; users/me/search/settings; push-tokens | User: users/register/verify, users/{id}/settings, users?query, devices |
| friends/{id}, requests/{id}/accept/reject | User: friends/{friendId}, requests/{id}/{decision}; контекст получателя через X-User-Id |
| assessment/start/submit/me/result | Quiz: assessments/sessions и sessions/{id}/submit/result |
| program/generate/me/next/progress | Quiz: assessment submit сохраняет программу; learning-program и learning-program/next |
| quiz/daily/{id}/start/hint/submit | Quiz: quizzes/daily, quizzes/{id}/attempts, quiz-attempts/{id}/hint/submit |
| competition singular | Quiz: competitions, invite/join/leaderboard |
| rating/me/friends | Quiz: ratings?period; выбор пользователей по UUID, friends читает User |
| analytics/<metric> | Event Receiver: analytics/{metric}, from/to |
| content/category/media/moderation/progress/reports | Content; внешнее администрирование через Admin facade |
| subscription/payment/receipt/webhook/maintenance | Payment, свой scheduler и PostgreSQL |
| inbox/templates/preferences/trigger/delivery | Notificator, локальный mock scheduler |
| shared datasource, SQL, ClickHouse migration bootstrapping | У каждого владельца свои миграции; Event Receiver владеет ClickHouse |

Отдельные read aliases (friends/requests, competition/{id}, отдельный rating/me)
не входят в утверждённый API; решения клиентов строятся на текущих ответах/IDs.
Полный список старых операций в inventory отличает unchanged от replaced/retired.

Удалены root production sources/resources, legacy tests, Dockerfile и Compose
legacy services/volumes declarations. Данные уже существующих Docker volumes не
удаляются. История задач сохранена. Root оставляет только verification tests;
старые тесты приложения заменены owner tests и Gateway E2E. Количество тестов
после удаления дубликатов закономерно уменьшается.

ArchUnit анализирует скомпилированные классы всех владельцев: controller не
обращается к repository, service/repository не зависят от controller, repository
доступен только application services/repositories. Межсервисные зависимости и
бизнес-модели в common запрещены; явные internal transport DTO допускаются.
Source guards проверяют datasource/FK/config, API baseline — method/path/operationId.
Gateway отвергает недокументированные public operations до forwarding; HEAD/OPTIONS
сохраняют стандартную HTTP/CORS семантику. Пять временных Admin routes внутри
Content удалены, domain integration tests используют internal Content API.
