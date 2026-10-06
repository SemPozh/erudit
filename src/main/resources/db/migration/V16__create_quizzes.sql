CREATE TABLE quizzes (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_quizzes_content ON quizzes(content_id, created_at);

CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    quiz_card_id UUID REFERENCES quiz_cards(id) ON DELETE SET NULL,
    position INTEGER NOT NULL,
    text VARCHAR(1000) NOT NULL,
    UNIQUE (quiz_id, position),
    CHECK (position >= 0)
);

CREATE INDEX idx_quiz_questions_card ON quiz_questions(quiz_card_id);

CREATE TABLE quiz_answer_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    text VARCHAR(1000) NOT NULL,
    correct BOOLEAN NOT NULL,
    UNIQUE (question_id, position),
    CHECK (position >= 0)
);
