CREATE TABLE assessment_grade_definitions (
    code VARCHAR(40) PRIMARY KEY,
    title VARCHAR(100) NOT NULL UNIQUE,
    min_score INTEGER NOT NULL UNIQUE,
    max_score INTEGER NOT NULL UNIQUE,
    CHECK (min_score >= 0 AND max_score <= 2000 AND min_score <= max_score)
);

INSERT INTO assessment_grade_definitions (code, title, min_score, max_score) VALUES
('NOVICE_I', 'Новичок I', 0, 149),
('NOVICE_II', 'Новичок II', 150, 299),
('NOVICE_III', 'Новичок III', 300, 449),
('EXPERT_I', 'Знаток I', 450, 599),
('EXPERT_II', 'Знаток II', 600, 749),
('EXPERT_III', 'Знаток III', 750, 899),
('ERUDITE_I', 'Эрудит I', 900, 1049),
('ERUDITE_II', 'Эрудит II', 1050, 1199),
('ERUDITE_III', 'Эрудит III', 1200, 1349),
('MASTER_I', 'Магистр I', 1350, 1499),
('MASTER_II', 'Магистр II', 1500, 1649),
('MASTER_III', 'Магистр III', 1650, 1799),
('LEGEND_I', 'Легенда I', 1800, 1899),
('LEGEND_II', 'Легенда II', 1900, 1999),
('LEGEND_III', 'Легенда III', 2000, 2000);

ALTER TABLE assessment_results ADD COLUMN grade_code VARCHAR(40);
UPDATE assessment_results r SET grade_code = (
    SELECT g.code FROM assessment_grade_definitions g WHERE r.er_score BETWEEN g.min_score AND g.max_score
);
ALTER TABLE assessment_results ALTER COLUMN grade_code SET NOT NULL;
ALTER TABLE assessment_results ADD CONSTRAINT fk_assessment_results_grade
    FOREIGN KEY (grade_code) REFERENCES assessment_grade_definitions(code);

ALTER TABLE assessment_profiles ADD COLUMN grade_code VARCHAR(40);
UPDATE assessment_profiles p SET grade_code = (
    SELECT g.code FROM assessment_grade_definitions g WHERE p.er_score BETWEEN g.min_score AND g.max_score
);
ALTER TABLE assessment_profiles ALTER COLUMN grade_code SET NOT NULL;
ALTER TABLE assessment_profiles ADD CONSTRAINT fk_assessment_profiles_grade
    FOREIGN KEY (grade_code) REFERENCES assessment_grade_definitions(code);
