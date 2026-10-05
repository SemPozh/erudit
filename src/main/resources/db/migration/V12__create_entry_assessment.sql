CREATE TABLE assessment_questions (
    id UUID PRIMARY KEY,
    topic VARCHAR(40) NOT NULL,
    text VARCHAR(1000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE assessment_answer_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES assessment_questions(id),
    position INTEGER NOT NULL,
    text VARCHAR(500) NOT NULL,
    correct BOOLEAN NOT NULL,
    UNIQUE (question_id, position)
);

CREATE TABLE assessment_sessions (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'SUBMITTED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE assessment_session_questions (
    assessment_id UUID NOT NULL REFERENCES assessment_sessions(id),
    question_id UUID NOT NULL REFERENCES assessment_questions(id),
    position INTEGER NOT NULL,
    PRIMARY KEY (assessment_id, question_id),
    UNIQUE (assessment_id, position)
);

CREATE TABLE assessment_answers (
    assessment_id UUID NOT NULL REFERENCES assessment_sessions(id),
    question_id UUID NOT NULL REFERENCES assessment_questions(id),
    answer_id UUID NOT NULL REFERENCES assessment_answer_options(id),
    correct BOOLEAN NOT NULL,
    PRIMARY KEY (assessment_id, question_id)
);

CREATE TABLE assessment_results (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL UNIQUE REFERENCES assessment_sessions(id),
    user_id VARCHAR(255) NOT NULL,
    correct_answers INTEGER NOT NULL,
    total_answers INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (correct_answers >= 0 AND correct_answers <= total_answers)
);

CREATE INDEX idx_assessment_results_user_created ON assessment_results(user_id, created_at);

INSERT INTO assessment_questions (id, topic, text) VALUES
('10000000-0000-0000-0000-000000000001', 'SCIENCE', 'Какая планета ближе всего к Солнцу?'),
('10000000-0000-0000-0000-000000000002', 'HISTORY', 'В каком веке началась промышленная революция?'),
('10000000-0000-0000-0000-000000000003', 'TECHNOLOGY', 'Что означает аббревиатура HTTP?'),
('10000000-0000-0000-0000-000000000004', 'CULTURE', 'Кто написал роман «Война и мир»?');

INSERT INTO assessment_answer_options (id, question_id, position, text, correct) VALUES
('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 0, 'Меркурий', TRUE),
('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 1, 'Венера', FALSE),
('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', 0, 'XVIII', TRUE),
('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 1, 'XX', FALSE),
('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000003', 0, 'HyperText Transfer Protocol', TRUE),
('20000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000003', 1, 'High Transfer Text Process', FALSE),
('20000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000004', 0, 'Лев Толстой', TRUE),
('20000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000004', 1, 'Антон Чехов', FALSE);
