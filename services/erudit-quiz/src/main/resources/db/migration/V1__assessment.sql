CREATE TABLE assessment_questions(id UUID PRIMARY KEY,prompt TEXT NOT NULL,options TEXT NOT NULL,correct_index INTEGER NOT NULL);
CREATE TABLE assessment_sessions(id UUID PRIMARY KEY,user_id UUID NOT NULL,status VARCHAR(24) NOT NULL,score INTEGER,grade VARCHAR(24),created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE learning_programs(user_id UUID PRIMARY KEY,grade VARCHAR(24) NOT NULL,progress INTEGER NOT NULL DEFAULT 0);
INSERT INTO assessment_questions VALUES('10000000-0000-0000-0000-000000000001','2 + 2 = ?','3|4|5',1),('10000000-0000-0000-0000-000000000002','Capital of France?','London|Paris|Rome',1);
