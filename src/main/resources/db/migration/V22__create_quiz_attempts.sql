CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL ,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    correct_answers INTEGER,
    score INTEGER,
    experience INTEGER
);

CREATE INDEX idx_quiz_attempts_user_quiz
    ON quiz_attempts(user_id, quiz_id, started_at);

CREATE TABLE quiz_attempt_answers (
    attempt_id UUID NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE CASCADE,
    answer_id UUID NOT NULL REFERENCES quiz_answer_options(id) ON DELETE CASCADE,
    correct BOOLEAN NOT NULL,
    PRIMARY KEY (attempt_id, question_id)
);
