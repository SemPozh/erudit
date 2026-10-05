ALTER TABLE assessment_results
    ADD COLUMN er_score INTEGER NOT NULL DEFAULT 0
        CHECK (er_score >= 0 AND er_score <= 2000);

CREATE TABLE assessment_result_topics (
    result_id UUID NOT NULL REFERENCES assessment_results(id) ON DELETE CASCADE,
    topic VARCHAR(40) NOT NULL,
    correct_answers INTEGER NOT NULL,
    total_answers INTEGER NOT NULL,
    er_score INTEGER NOT NULL,
    PRIMARY KEY (result_id, topic),
    CHECK (correct_answers >= 0 AND correct_answers <= total_answers),
    CHECK (er_score >= 0 AND er_score <= 2000)
);

CREATE TABLE assessment_profiles (
    user_id VARCHAR(255) PRIMARY KEY,
    latest_result_id UUID NOT NULL REFERENCES assessment_results(id),
    er_score INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (er_score >= 0 AND er_score <= 2000)
);
