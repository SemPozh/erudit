# Справочник аналитических событий

Канонический источник для кода — `EventType`. Во внешних запросах и сообщениях используется
строковое значение из таблицы. Неизвестные значения отклоняются до записи в ClickHouse.

| Группа | Типы событий | Назначение |
| --- | --- | --- |
| `NAVIGATION` | `screen_opened`, `button_clicked`, `link_clicked` | Переходы по приложению и клики |
| `CONTENT` | `content_viewed`, `content_started`, `content_completed` | Воронка изучения контента |
| `QUIZ` | `quiz_started`, `quiz_answered`, `quiz_completed`, `competition_completed` | Квизы и соревнования |
| `SOCIAL` | `friend_requested`, `friend_added`, `friend_overtaken` | Дружба и изменения рейтинга друзей |
| `MONETIZATION` | `subscription_started`, `payment_succeeded`, `subscription_cancelled`, `subscription_expired` | Оплаты и жизненный цикл подписки |
| `NOTIFICATION` | `notification_delivered`, `notification_opened`, `notification_clicked` | Доставка и конверсия уведомлений |

Группа сохраняется рядом с типом события. Аналитические запросы могут агрегировать данные по
группе, а детальные панели — по конкретному типу.
