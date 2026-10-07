ALTER TABLE quiz_attempts ADD COLUMN daily_date DATE;

CREATE UNIQUE INDEX uq_quiz_attempts_user_daily
    ON quiz_attempts(user_id, daily_date);