DROP TABLE IF EXISTS feedback_events;
DROP TABLE IF EXISTS search_sessions;
DROP TABLE IF EXISTS consents;
DROP TABLE IF EXISTS search_requests;
DROP TABLE IF EXISTS album_members;
DROP TABLE IF EXISTS albums;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS audit_events;

CREATE TABLE users (
  id VARCHAR(64) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  role VARCHAR(32) NOT NULL
);

CREATE TABLE albums (
  id VARCHAR(64) PRIMARY KEY,
  owner_user_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  photo_count INTEGER NOT NULL,
  index_status VARCHAR(32) NOT NULL
);

CREATE TABLE album_members (
  album_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  PRIMARY KEY (album_id, user_id)
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
  expires_at TIMESTAMP WITH TIME ZONE
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
  revoked_at TIMESTAMP WITH TIME ZONE
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
  results_json CLOB,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE feedback_events (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  result_id VARCHAR(64) NOT NULL,
  actor_user_id VARCHAR(64) NOT NULL,
  relevance VARCHAR(32) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE audit_events (
  id VARCHAR(64) PRIMARY KEY,
  actor_user_id VARCHAR(64),
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64),
  policy_version VARCHAR(64) NOT NULL,
  metadata CLOB,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
