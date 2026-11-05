# Каталог HTTP API ЛР2

Источник: service-owned `openapi.yaml`; обновить: `python scripts/api-inventory.py`.
Проверка актуальности: `python scripts/api-inventory.py --check` (PyYAML).

## Публичные операции через Gateway

Всего: 83. UUID, query, тела, статусы и примеры — в Gateway Swagger.

| Владелец | Метод | Путь | operationId |
| --- | --- | --- | --- |
| erudit-admin | GET | `/api/v1/admin/content/moderation` | `listModerationQueue` |
| erudit-admin | POST | `/api/v1/admin/content/{id}/approve` | `approveContent` |
| erudit-admin | POST | `/api/v1/admin/content/{id}/reject` | `rejectContent` |
| erudit-admin | GET | `/api/v1/admin/content/reports` | `listContentReports` |
| erudit-admin | POST | `/api/v1/admin/content/reports/{id}/resolve` | `resolveContentReport` |
| erudit-admin | GET | `/api/v1/admin/users` | `listAdminUsers` |
| erudit-admin | POST | `/api/v1/admin/users/{id}/block` | `blockUser` |
| erudit-admin | POST | `/api/v1/admin/notifications/send-many` | `sendManyNotifications` |
| erudit-admin | GET | `/api/v1/admin/audit` | `listAuditLog` |
| erudit-admin | GET | `/api/v1/admin/users/{id}/subscription` | `viewUserSubscription` |
| erudit-admin | GET | `/api/v1/admin/users/{id}/payments` | `viewUserPayments` |
| erudit-content | GET | `/api/v1/content` | `listContent` |
| erudit-content | POST | `/api/v1/content` | `createContent` |
| erudit-content | GET | `/api/v1/content/formats` | `listFormats` |
| erudit-content | GET | `/api/v1/content/{id}` | `getContent` |
| erudit-content | PUT | `/api/v1/content/{id}` | `updateContent` |
| erudit-content | DELETE | `/api/v1/content/{id}` | `archiveContent` |
| erudit-content | POST | `/api/v1/media` | `uploadMedia` |
| erudit-content | GET | `/api/v1/media/{id}` | `getMedia` |
| erudit-content | GET | `/api/v1/content/categories` | `listCategories` |
| erudit-content | POST | `/api/v1/content/categories` | `createCategory` |
| erudit-content | GET | `/api/v1/content/categories/{id}` | `getCategory` |
| erudit-content | PUT | `/api/v1/content/categories/{id}` | `updateCategory` |
| erudit-content | DELETE | `/api/v1/content/categories/{id}` | `deleteCategory` |
| erudit-content | POST | `/api/v1/content/{id}/report` | `reportContent` |
| erudit-content | GET | `/api/v1/content/feed` | `getContentFeed` |
| erudit-content | GET | `/api/v1/content/{id}/quiz-cards` | `getContentQuizCards` |
| erudit-content | GET | `/api/v1/content/{id}/progress` | `getContentProgress` |
| erudit-content | POST | `/api/v1/content/{id}/view` | `markContentViewed` |
| erudit-content | POST | `/api/v1/content/{id}/complete` | `completeContent` |
| erudit-event-receiver | POST | `/api/v1/events` | `ingestEvent` |
| erudit-event-receiver | POST | `/api/v1/events/batch` | `ingestEventBatch` |
| erudit-event-receiver | GET | `/api/v1/analytics/{metric}` | `getAnalyticsMetric` |
| erudit-notificator | GET | `/api/v1/notifications/preferences` | `getPreferences` |
| erudit-notificator | PUT | `/api/v1/notifications/preferences` | `updatePreferences` |
| erudit-notificator | GET | `/api/v1/notifications` | `listNotifications` |
| erudit-notificator | POST | `/api/v1/notifications/{id}/read` | `markRead` |
| erudit-notificator | POST | `/api/v1/notifications/read-all` | `markAllRead` |
| erudit-notificator | GET | `/api/v1/notifications/unread-count` | `unreadCount` |
| erudit-notificator | GET | `/api/v1/notifications/{id}/delivery-attempts` | `deliveryAttempts` |
| erudit-notificator | GET | `/api/v1/notifications/templates` | `listTemplates` |
| erudit-notificator | POST | `/api/v1/notifications/templates` | `createTemplate` |
| erudit-notificator | GET | `/api/v1/notifications/templates/{id}` | `getTemplate` |
| erudit-notificator | PUT | `/api/v1/notifications/templates/{id}` | `updateTemplate` |
| erudit-notificator | DELETE | `/api/v1/notifications/templates/{id}` | `deleteTemplate` |
| erudit-payment | POST | `/api/v1/subscriptions` | `createSubscription` |
| erudit-payment | GET | `/api/v1/subscriptions/me` | `getMySubscription` |
| erudit-payment | POST | `/api/v1/subscriptions/{id}/cancel` | `cancelSubscription` |
| erudit-payment | GET | `/api/v1/subscriptions/payments` | `listPayments` |
| erudit-payment | GET | `/api/v1/subscriptions/plans` | `listSubscriptionPlans` |
| erudit-payment | POST | `/api/v1/subscriptions/mobile/verify` | `verifyMobileReceipt` |
| erudit-payment | POST | `/api/v1/payments/webhooks/{provider}` | `paymentWebhook` |
| erudit-quiz | POST | `/api/v1/competitions` | `createCompetition` |
| erudit-quiz | POST | `/api/v1/competitions/{id}/invite` | `inviteCompetition` |
| erudit-quiz | POST | `/api/v1/competitions/{id}/join` | `joinCompetition` |
| erudit-quiz | GET | `/api/v1/competitions/{id}/leaderboard` | `competitionLeaderboard` |
| erudit-quiz | GET | `/api/v1/ratings` | `globalRatings` |
| erudit-quiz | GET | `/api/v1/quizzes` | `listQuizzes` |
| erudit-quiz | POST | `/api/v1/quizzes` | `createQuiz` |
| erudit-quiz | GET | `/api/v1/quizzes/daily` | `dailyQuiz` |
| erudit-quiz | POST | `/api/v1/quizzes/{id}/attempts` | `startQuiz` |
| erudit-quiz | POST | `/api/v1/quiz-attempts/{id}/hint` | `quizHint` |
| erudit-quiz | POST | `/api/v1/quiz-attempts/{id}/submit` | `submitQuiz` |
| erudit-quiz | GET | `/api/v1/assessments/questions` | `listAssessmentQuestions` |
| erudit-quiz | POST | `/api/v1/assessments/sessions` | `startAssessment` |
| erudit-quiz | POST | `/api/v1/assessments/sessions/{id}/submit` | `submitAssessment` |
| erudit-quiz | GET | `/api/v1/assessments/sessions/{id}/result` | `getAssessmentResult` |
| erudit-quiz | GET | `/api/v1/learning-program` | `getLearningProgram` |
| erudit-quiz | GET | `/api/v1/learning-program/next` | `getNextLearningStep` |
| erudit-user | POST | `/api/v1/users/register` | `register` |
| erudit-user | POST | `/api/v1/users/verify` | `verifyEmail` |
| erudit-user | GET | `/api/v1/users` | `searchUsers` |
| erudit-user | GET | `/api/v1/users/{id}` | `getUser` |
| erudit-user | PUT | `/api/v1/users/{id}` | `updateUser` |
| erudit-user | DELETE | `/api/v1/users/{id}` | `deleteUser` |
| erudit-user | GET | `/api/v1/friends` | `listFriends` |
| erudit-user | POST | `/api/v1/friends/requests` | `requestFriend` |
| erudit-user | POST | `/api/v1/friends/requests/{id}/{decision}` | `decideFriendRequest` |
| erudit-user | DELETE | `/api/v1/friends/{friendId}` | `removeFriend` |
| erudit-user | POST | `/api/v1/devices` | `registerDevice` |
| erudit-user | DELETE | `/api/v1/devices/{id}` | `revokeDevice` |
| erudit-user | GET | `/api/v1/users/{id}/settings` | `getSettings` |
| erudit-user | PUT | `/api/v1/users/{id}/settings` | `updateSettings` |

## Внутренние операции

Всего: 12. Только частная сеть и Feign; Gateway их не публикует.

| Владелец | Метод | Путь | operationId |
| --- | --- | --- | --- |
| erudit-ai-platform | POST | `/internal/v1/ai/quiz-generations` | `generateQuiz` |
| erudit-ai-platform | POST | `/internal/v1/ai/content-cards` | `generateContentCard` |
| erudit-content | POST | `/internal/v1/moderation` | `moderateInternal` |
| erudit-content | GET | `/internal/v1/content/moderation` | `internalListModerationQueue` |
| erudit-content | GET | `/internal/v1/content/reports` | `internalListContentReports` |
| erudit-content | POST | `/internal/v1/content/reports/{id}/resolve` | `internalResolveContentReport` |
| erudit-notificator | POST | `/internal/v1/notifications/enqueue` | `enqueue` |
| erudit-notificator | POST | `/internal/v1/notifications/enqueue/batch` | `enqueueBatch` |
| erudit-payment | GET | `/internal/v1/entitlements/{userId}` | `getEntitlement` |
| erudit-user | GET | `/internal/v1/users/{id}` | `lookupUser` |
| erudit-user | GET | `/internal/v1/admin/users` | `adminSearchUsers` |
| erudit-user | POST | `/internal/v1/admin/users/{id}/block` | `adminBlockUser` |

## Платформа и диагностика

| Владелец | Метод/путь | Доступ |
| --- | --- | --- |
| Gateway | GET `/swagger-ui/index.html`, `/swagger-ui/**` | public |
| Gateway | GET `/openapi/swagger-config`, `/openapi/{service-name}` | public; 8 business specs |
| Все приложения | GET `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/prometheus` | извне только Gateway |
| Config Server | GET `/{application}/{profile}` | частная сеть; native repository |
| Eureka | GET `/`, `/eureka/apps`, `/eureka/apps/{app}`; регистрация/heartbeat через Eureka client | частная сеть |

Отладочные порты opt-in/loopback: `docker-compose.debug.yml`. Native Config Server
также предоставляет стандартные форматы Spring Cloud Config; это служебный API,
не часть бизнес-контракта Gateway. HEAD/OPTIONS следуют HTTP/CORS семантике.
