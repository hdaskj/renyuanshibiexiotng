ALTER TABLE users ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE albums ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE album_members ADD COLUMN joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE albums ADD CONSTRAINT fk_album_owner
  FOREIGN KEY (owner_user_id) REFERENCES users(id);
ALTER TABLE album_members ADD CONSTRAINT fk_member_album
  FOREIGN KEY (album_id) REFERENCES albums(id);
ALTER TABLE album_members ADD CONSTRAINT fk_member_user
  FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE search_requests ADD CONSTRAINT fk_request_album
  FOREIGN KEY (album_id) REFERENCES albums(id);
ALTER TABLE search_requests ADD CONSTRAINT fk_request_requester
  FOREIGN KEY (requester_user_id) REFERENCES users(id);
ALTER TABLE search_requests ADD CONSTRAINT fk_request_decider
  FOREIGN KEY (decided_by_user_id) REFERENCES users(id);
ALTER TABLE consents ADD CONSTRAINT fk_consent_request
  FOREIGN KEY (request_id) REFERENCES search_requests(id);
ALTER TABLE consents ADD CONSTRAINT fk_consent_album
  FOREIGN KEY (album_id) REFERENCES albums(id);
ALTER TABLE consents ADD CONSTRAINT fk_consent_subject
  FOREIGN KEY (subject_user_id) REFERENCES users(id);
ALTER TABLE search_sessions ADD CONSTRAINT fk_session_request
  FOREIGN KEY (request_id) REFERENCES search_requests(id);
ALTER TABLE search_sessions ADD CONSTRAINT fk_session_actor
  FOREIGN KEY (actor_user_id) REFERENCES users(id);
ALTER TABLE audit_events ADD CONSTRAINT fk_audit_actor
  FOREIGN KEY (actor_user_id) REFERENCES users(id);
ALTER TABLE audit_events ADD COLUMN album_id VARCHAR(64);
ALTER TABLE audit_events ADD CONSTRAINT fk_audit_album
  FOREIGN KEY (album_id) REFERENCES albums(id);

ALTER TABLE search_sessions ADD COLUMN sample_sha256 VARCHAR(64);
ALTER TABLE search_sessions ADD COLUMN completed_at TIMESTAMPTZ;

CREATE TABLE search_results (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL REFERENCES search_sessions(id) ON DELETE CASCADE,
  photo_id VARCHAR(64) NOT NULL,
  rank_number INTEGER NOT NULL CHECK (rank_number > 0),
  overall_score INTEGER NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
  face_score INTEGER NOT NULL CHECK (face_score BETWEEN 0 AND 100),
  outfit_score INTEGER NOT NULL CHECK (outfit_score BETWEEN 0 AND 100),
  scene_score INTEGER NOT NULL CHECK (scene_score BETWEEN 0 AND 100),
  title VARCHAR(256) NOT NULL,
  album_id VARCHAR(64) NOT NULL,
  album_name VARCHAR(128) NOT NULL,
  scene_label VARCHAR(128),
  outfit_label VARCHAR(128),
  photo_date VARCHAR(64),
  summary VARCHAR(1000),
  identity_disclaimer VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_result_session_photo UNIQUE (session_id, photo_id),
  CONSTRAINT uq_result_session_rank UNIQUE (session_id, rank_number)
);

ALTER TABLE feedback_events ADD COLUMN reason_code VARCHAR(64);
ALTER TABLE feedback_events ADD COLUMN retention_until TIMESTAMPTZ;

CREATE TABLE deletion_jobs (
  id VARCHAR(64) PRIMARY KEY,
  consent_id VARCHAR(64) NOT NULL REFERENCES consents(id),
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error VARCHAR(1000),
  due_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_events (
  id VARCHAR(64) PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload_json TEXT NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
  attempts INTEGER NOT NULL DEFAULT 0,
  available_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_results_session_rank ON search_results (session_id, rank_number);
CREATE INDEX idx_audit_album_occurred ON audit_events (album_id, occurred_at DESC);
CREATE INDEX idx_audit_actor_occurred ON audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX idx_feedback_retention ON feedback_events (retention_until);
CREATE INDEX idx_deletion_status_due ON deletion_jobs (status, due_at);
CREATE INDEX idx_outbox_status_available ON outbox_events (status, available_at);
