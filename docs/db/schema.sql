DROP TABLE IF EXISTS outbox_events;
DROP TABLE IF EXISTS deletion_jobs;
DROP TABLE IF EXISTS feedback_events;
DROP TABLE IF EXISTS search_results;
DROP TABLE IF EXISTS search_sessions;
DROP TABLE IF EXISTS consents;
DROP TABLE IF EXISTS search_requests;
DROP TABLE IF EXISTS audit_events;
DROP TABLE IF EXISTS album_members;
DROP TABLE IF EXISTS albums;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id VARCHAR(64) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  role VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE albums (
  id VARCHAR(64) PRIMARY KEY,
  owner_user_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  photo_count INTEGER NOT NULL DEFAULT 0,
  index_status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_album_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
);

CREATE TABLE album_members (
  album_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (album_id, user_id),
  CONSTRAINT fk_member_album FOREIGN KEY (album_id) REFERENCES albums(id),
  CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT ck_member_status CHECK (status IN ('INVITED', 'ACTIVE', 'REVOKED'))
);

CREATE TABLE search_requests (
  id VARCHAR(64) PRIMARY KEY,
  album_id VARCHAR(64) NOT NULL,
  requester_user_id VARCHAR(64) NOT NULL,
  purpose_code VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  requested_days INTEGER NOT NULL,
  approved_days INTEGER,
  decided_by_user_id VARCHAR(64),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  decided_at TIMESTAMP WITH TIME ZONE,
  expires_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT fk_request_album FOREIGN KEY (album_id) REFERENCES albums(id),
  CONSTRAINT fk_request_requester FOREIGN KEY (requester_user_id) REFERENCES users(id),
  CONSTRAINT fk_request_decider FOREIGN KEY (decided_by_user_id) REFERENCES users(id),
  CONSTRAINT ck_request_days CHECK (requested_days BETWEEN 1 AND 14),
  CONSTRAINT ck_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'EXPIRED', 'REVOKED'))
);

CREATE TABLE consents (
  id VARCHAR(64) PRIMARY KEY,
  request_id VARCHAR(64) NOT NULL UNIQUE,
  album_id VARCHAR(64) NOT NULL,
  subject_user_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  policy_version VARCHAR(64) NOT NULL,
  effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT fk_consent_request FOREIGN KEY (request_id) REFERENCES search_requests(id),
  CONSTRAINT fk_consent_album FOREIGN KEY (album_id) REFERENCES albums(id),
  CONSTRAINT fk_consent_subject FOREIGN KEY (subject_user_id) REFERENCES users(id),
  CONSTRAINT ck_consent_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED'))
);

CREATE TABLE search_sessions (
  id VARCHAR(64) PRIMARY KEY,
  request_id VARCHAR(64) NOT NULL,
  actor_user_id VARCHAR(64) NOT NULL,
  sort_mode VARCHAR(32) NOT NULL,
  diversity_enabled BOOLEAN NOT NULL,
  query_text VARCHAR(500),
  status VARCHAR(32) NOT NULL,
  model_version VARCHAR(128),
  consent_version VARCHAR(64),
  sample_sha256 VARCHAR(64),
  results_json TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  completed_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT fk_session_request FOREIGN KEY (request_id) REFERENCES search_requests(id),
  CONSTRAINT fk_session_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
  CONSTRAINT ck_session_sort CHECK (sort_mode IN ('OVERALL', 'FACE', 'OUTFIT', 'SCENE')),
  CONSTRAINT ck_session_status CHECK (status IN ('QUEUED', 'EXTRACTING', 'SEARCHING', 'COMPLETED', 'FAILED', 'EXPIRED', 'REVOKED'))
);

CREATE TABLE search_results (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  photo_id VARCHAR(64) NOT NULL,
  rank_number INTEGER NOT NULL,
  overall_score INTEGER NOT NULL,
  face_score INTEGER NOT NULL,
  outfit_score INTEGER NOT NULL,
  scene_score INTEGER NOT NULL,
  title VARCHAR(256) NOT NULL,
  album_id VARCHAR(64) NOT NULL,
  album_name VARCHAR(128) NOT NULL,
  scene_label VARCHAR(128),
  outfit_label VARCHAR(128),
  photo_date VARCHAR(64),
  summary VARCHAR(1000),
  identity_disclaimer VARCHAR(128) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_result_session FOREIGN KEY (session_id) REFERENCES search_sessions(id) ON DELETE CASCADE,
  CONSTRAINT uq_result_session_photo UNIQUE (session_id, photo_id),
  CONSTRAINT uq_result_session_rank UNIQUE (session_id, rank_number),
  CONSTRAINT ck_result_rank CHECK (rank_number > 0),
  CONSTRAINT ck_result_scores CHECK (
    overall_score BETWEEN 0 AND 100
    AND face_score BETWEEN 0 AND 100
    AND outfit_score BETWEEN 0 AND 100
    AND scene_score BETWEEN 0 AND 100
  )
);

CREATE TABLE feedback_events (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  result_id VARCHAR(64) NOT NULL,
  actor_user_id VARCHAR(64) NOT NULL,
  relevance VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64),
  retention_until TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_feedback_session FOREIGN KEY (session_id) REFERENCES search_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_feedback_result FOREIGN KEY (result_id) REFERENCES search_results(id) ON DELETE CASCADE,
  CONSTRAINT fk_feedback_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
  CONSTRAINT ck_feedback_relevance CHECK (relevance IN ('RELEVANT', 'IRRELEVANT', 'UNDO'))
);

CREATE TABLE audit_events (
  id VARCHAR(64) PRIMARY KEY,
  album_id VARCHAR(64),
  actor_user_id VARCHAR(64),
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64),
  policy_version VARCHAR(64) NOT NULL,
  metadata TEXT,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_audit_album FOREIGN KEY (album_id) REFERENCES albums(id),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE TABLE deletion_jobs (
  id VARCHAR(64) PRIMARY KEY,
  consent_id VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error VARCHAR(1000),
  due_at TIMESTAMP WITH TIME ZONE NOT NULL,
  completed_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_deletion_consent FOREIGN KEY (consent_id) REFERENCES consents(id),
  CONSTRAINT ck_deletion_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE outbox_events (
  id VARCHAR(64) PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload_json TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  available_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_members_user ON album_members (user_id, status);
CREATE INDEX idx_requests_album_created ON search_requests (album_id, created_at DESC);
CREATE INDEX idx_requests_requester ON search_requests (requester_user_id, created_at DESC);
CREATE INDEX idx_consents_expiry ON consents (status, expires_at);
CREATE INDEX idx_sessions_request ON search_sessions (request_id);
CREATE INDEX idx_results_session_rank ON search_results (session_id, rank_number);
CREATE INDEX idx_feedback_retention ON feedback_events (retention_until);
CREATE INDEX idx_audit_album_occurred ON audit_events (album_id, occurred_at DESC);
CREATE INDEX idx_audit_actor_occurred ON audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX idx_deletion_status_due ON deletion_jobs (status, due_at);
CREATE INDEX idx_outbox_status_available ON outbox_events (status, available_at);
