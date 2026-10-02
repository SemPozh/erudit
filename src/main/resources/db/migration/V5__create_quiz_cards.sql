CREATE TABLE quiz_cards (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES content(id),
    position INTEGER NOT NULL,
    fact VARCHAR(2000) NOT NULL,
    question VARCHAR(1000) NOT NULL,
    correct_answer VARCHAR(2000) NOT NULL,
    UNIQUE (content_id, position)
);

CREATE INDEX idx_quiz_cards_content ON quiz_cards(content_id, position);

CREATE TABLE quiz_card_answers (
    quiz_card_id UUID NOT NULL REFERENCES quiz_cards(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    answer VARCHAR(2000) NOT NULL,
    PRIMARY KEY (quiz_card_id, position)
);
