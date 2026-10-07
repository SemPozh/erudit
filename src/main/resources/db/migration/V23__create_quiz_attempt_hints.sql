CREATE TABLE quiz_attempt_hints (
    attempt_id UUID NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE CASCADE,
    used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (attempt_id, question_id)
);