# Gateway E2E: проверенный прогон 5 ноября 2026

Локальный Docker Desktop, Java 25, Gateway :18089, заново созданные demo volumes.
Только синтетические данные; Authorization и SQL не использовались.

```powershell
./scripts/reset-demo.ps1
./scripts/smoke-demo-contracts.ps1
./scripts/smoke-e2e.ps1
./scripts/smoke-resilience.ps1
```

Перед этим прогоном все 11 образов пересобраны и проверены на Git SHA/UTC labels,
content digest и UID 10001. HTTP contract suite: 50 PASS. Gradle
`test openApiValidate build`: PASS, 161 tests, 0 failures, 0 skipped.
Ниже — 33 шага E2E; UUID относятся только к временному demo context.

| Step | Result |
| --- | --- |
| POST /api/v1/users/register [201] | PASS |
| POST /api/v1/users/verify [204] | PASS |
| PUT /api/v1/users/04fd4a65-7e56-415f-b276-e7ef0b3dd61d [200] | PASS |
| GET /api/v1/users/04fd4a65-7e56-415f-b276-e7ef0b3dd61d [200] | PASS |
| POST /api/v1/content/categories [201] | PASS |
| POST /api/v1/content [201] | PASS |
| POST /api/v1/admin/content/78a147ee-daff-46b7-9643-e7d81ce158f3/approve [200] | PASS |
| GET /api/v1/content/78a147ee-daff-46b7-9643-e7d81ce158f3 [200] | PASS |
| POST /api/v1/content/78a147ee-daff-46b7-9643-e7d81ce158f3/complete [200] | PASS |
| POST /api/v1/assessments/sessions [201] | PASS |
| GET /api/v1/assessments/questions [200] | PASS |
| POST /api/v1/assessments/sessions/7f985c0a-2f88-45bd-9e80-d283f11ac392/submit [200] | PASS |
| GET /api/v1/learning-program [200] | PASS |
| POST /api/v1/quizzes [201] | PASS |
| POST /api/v1/quizzes/3ba3c4ad-a743-4c68-bc25-99cd37549ae5/attempts [201] | PASS |
| POST /api/v1/quiz-attempts/a72135d2-ed29-4c20-8b12-85d2b64e2f13/hint [200] | PASS |
| POST /api/v1/quiz-attempts/a72135d2-ed29-4c20-8b12-85d2b64e2f13/submit [200] | PASS |
| POST /api/v1/competitions [201] | PASS |
| POST /api/v1/competitions/dd80d3fa-f780-4893-896d-881625422ace/invite [202] | PASS |
| POST /api/v1/competitions/dd80d3fa-f780-4893-896d-881625422ace/join [204] | PASS |
| GET /api/v1/competitions/dd80d3fa-f780-4893-896d-881625422ace/leaderboard [200] | PASS |
| GET /api/v1/ratings?period=ALL [200] | PASS |
| POST /api/v1/subscriptions [201] | PASS |
| GET /api/v1/content/11000000-0000-0000-0000-000000000003 [200] | PASS |
| GET /api/v1/subscriptions/payments [200] | PASS |
| POST /api/v1/admin/notifications/send-many [200] | PASS |
| GET /api/v1/notifications [200] | PASS |
| POST /api/v1/notifications/fb733e85-6fd9-4206-860b-047270f19a17/read [204] | PASS |
| POST /api/v1/events [202] | PASS |
| GET /api/v1/analytics/sessions?from=2026-11-04T22:59:43Z&to=2026-11-04T23:09:43Z [200] | PASS |
| GET /api/v1/admin/audit [200] | PASS |
| POST /api/v1/subscriptions/6ece259f-3534-4f4f-85a1-aa09dd84a95a/cancel [200] | PASS |
| DELETE /api/v1/content/78a147ee-daff-46b7-9643-e7d81ce158f3 [204] | PASS |

## Dependency failures/recovery

| Scenario | Result |
| --- | --- |
| AI stopped; local cards, circuit OPEN confirmed by Prometheus | PASS |
| open circuit and Quiz fallback keep writes available | PASS |
| payment stopped; premium fails closed, free content remains available | PASS |
| AI recovery; circuit CLOSED confirmed by Prometheus | PASS |
| payment recovery; premium entitlement and Gateway cancellation restored | PASS |

Normal-profile smoke: PASS — 19 healthy containers, только Gateway публикует порт,
свежие собственные тома, User restart без rebuild и восстановление через Gateway.
Временный normal project и его тома/сети удалены после проверки.
