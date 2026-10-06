CREATE TABLE learning_programs (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE program_modules (
    id UUID PRIMARY KEY,
    program_id UUID NOT NULL REFERENCES learning_programs(id) ON DELETE CASCADE,
    topic VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL,
    UNIQUE (program_id, topic),
    UNIQUE (program_id, position),
    CHECK (position >= 0)
);

CREATE TABLE program_lessons (
    id UUID PRIMARY KEY,
    module_id UUID NOT NULL REFERENCES program_modules(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES content(id),
    quiz_id UUID NOT NULL REFERENCES quizzes(id),
    position INTEGER NOT NULL,
    UNIQUE (module_id, position),
    UNIQUE (module_id, content_id),
    CHECK (position >= 0)
);

CREATE INDEX idx_program_lessons_content ON program_lessons(content_id);
