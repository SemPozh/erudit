CREATE TABLE competitions (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    quiz_id UUID NOT NULL REFERENCES quizzes(id),
    creator_id VARCHAR(255) NOT NULL,
    rules VARCHAR(2000),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE TABLE competition_participants (
    competition_id UUID NOT NULL REFERENCES competitions(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (competition_id, user_id)
);