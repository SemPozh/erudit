CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT ck_categories_name_not_blank CHECK (btrim(name) <> '')
);

INSERT INTO categories (id, name) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Наука'),
    ('10000000-0000-0000-0000-000000000002', 'История'),
    ('10000000-0000-0000-0000-000000000003', 'Технологии');
