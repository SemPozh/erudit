ALTER TABLE assessment_results ADD COLUMN feedback VARCHAR(2000);
UPDATE assessment_results
SET feedback = 'Результат рассчитан. Пройдите тестирование повторно для персональной обратной связи.';
ALTER TABLE assessment_results ALTER COLUMN feedback SET NOT NULL;

CREATE TABLE assessment_result_recommendations (
    result_id UUID NOT NULL REFERENCES assessment_results(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    text VARCHAR(1000) NOT NULL,
    PRIMARY KEY (result_id, position),
    CHECK (position >= 0)
);
