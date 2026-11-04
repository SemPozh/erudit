ALTER TABLE content ADD CONSTRAINT content_status_allowed CHECK(status IN ('PENDING_MODERATION','PUBLISHED','REJECTED','ARCHIVED'));
CREATE TABLE content_progress (
 user_id UUID NOT NULL, content_id UUID NOT NULL REFERENCES content(id),
 status VARCHAR(20) NOT NULL CHECK(status IN ('VIEWED','COMPLETED')),
 viewed_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ,
 PRIMARY KEY(user_id,content_id),
 CHECK((status='VIEWED' AND completed_at IS NULL) OR (status='COMPLETED' AND completed_at>=viewed_at))
);
CREATE TABLE content_quiz_cards (
 id UUID PRIMARY KEY, content_id UUID NOT NULL REFERENCES content(id) ON DELETE CASCADE,
 position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 4), fact TEXT NOT NULL,
 question TEXT NOT NULL, answers TEXT[] NOT NULL, UNIQUE(content_id,position)
);
CREATE TABLE content_reports (
 id UUID PRIMARY KEY, content_id UUID NOT NULL REFERENCES content(id), reason VARCHAR(500) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('OPEN','RESOLVED')), created_at TIMESTAMPTZ NOT NULL,
 decision VARCHAR(20) CHECK(decision IN ('ACCEPT','REJECT')), resolution_comment VARCHAR(2000),
 resolved_at TIMESTAMPTZ, resolved_by UUID,
 CHECK((status='OPEN' AND decision IS NULL AND resolved_at IS NULL AND resolved_by IS NULL)
    OR (status='RESOLVED' AND decision IS NOT NULL AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL))
);
CREATE INDEX reports_open_queue ON content_reports(status,created_at,id);
CREATE TABLE content_moderation_audit (
 id UUID PRIMARY KEY, actor_id UUID NOT NULL, content_id UUID NOT NULL REFERENCES content(id),
 report_id UUID REFERENCES content_reports(id), action VARCHAR(40) NOT NULL,
 reason VARCHAR(2000), occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX content_moderation_queue ON content(status,created_at,id);
