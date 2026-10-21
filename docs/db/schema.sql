-- AI-03 相寻 metadata schema (PostgreSQL 16+)
-- Vector values live in Milvus/Faiss. This schema stores identifiers,
-- consent state, business metadata and audit references only.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  external_subject  TEXT NOT NULL UNIQUE,
  display_name      TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE albums (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id     UUID NOT NULL REFERENCES users(id),
  name              TEXT NOT NULL,
  event_date        DATE,
  photo_count       INTEGER NOT NULL DEFAULT 0 CHECK (photo_count >= 0),
  index_status      TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (index_status IN ('PENDING', 'INDEXING', 'READY', 'CLEANUP_PENDING', 'FAILED')),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE album_memberships (
  album_id          UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
  user_id           UUID NOT NULL REFERENCES users(id),
  role              TEXT NOT NULL CHECK (role IN ('OWNER', 'PARTICIPANT')),
  member_status     TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK (member_status IN ('INVITED', 'ACTIVE', 'REVOKED')),
  joined_at         TIMESTAMPTZ,
  PRIMARY KEY (album_id, user_id)
);

CREATE TABLE consent_records (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  album_id          UUID NOT NULL REFERENCES albums(id),
  subject_user_id   UUID NOT NULL REFERENCES users(id),
  granted_by_user_id UUID NOT NULL REFERENCES users(id),
  purpose_code      TEXT NOT NULL CHECK (purpose_code = 'FIND_MY_PHOTOS'),
  scope             JSONB NOT NULL DEFAULT '{"subjects":["SELF"],"export":false}'::jsonb,
  status            TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('REQUESTED', 'ACTIVE', 'EXPIRED', 'REVOKED', 'DENIED')),
  policy_version    TEXT NOT NULL,
  effective_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at        TIMESTAMPTZ,
  revoked_at        TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (expires_at IS NULL OR expires_at > effective_at),
  CHECK ((status = 'REVOKED' AND revoked_at IS NOT NULL) OR status <> 'REVOKED')
);

CREATE INDEX idx_consent_active
  ON consent_records (album_id, subject_user_id, expires_at)
  WHERE status = 'ACTIVE';

CREATE TABLE search_requests (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  album_id          UUID NOT NULL REFERENCES albums(id),
  requester_user_id UUID NOT NULL REFERENCES users(id),
  purpose_code      TEXT NOT NULL CHECK (purpose_code = 'FIND_MY_PHOTOS'),
  status            TEXT NOT NULL DEFAULT 'REQUESTED'
                    CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'EXPIRED', 'REVOKED')),
  statement         TEXT CHECK (char_length(statement) <= 500),
  requested_days    INTEGER NOT NULL DEFAULT 7 CHECK (requested_days BETWEEN 1 AND 14),
  approved_days     INTEGER CHECK (approved_days BETWEEN 1 AND 7),
  decided_by_user_id UUID REFERENCES users(id),
  decided_at        TIMESTAMPTZ,
  expires_at        TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((status = 'APPROVED' AND decided_at IS NOT NULL AND expires_at IS NOT NULL) OR status <> 'APPROVED')
);

CREATE INDEX idx_search_requests_owner_queue
  ON search_requests (album_id, created_at)
  WHERE status = 'REQUESTED';

CREATE TABLE photos (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  album_id          UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
  storage_object_key TEXT NOT NULL UNIQUE,
  media_type        TEXT NOT NULL,
  width             INTEGER CHECK (width > 0),
  height            INTEGER CHECK (height > 0),
  photographed_at   TIMESTAMPTZ,
  scene_label       TEXT,
  outfit_label      TEXT,
  index_status      TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (index_status IN ('PENDING', 'INDEXING', 'READY', 'CLEANUP_PENDING', 'FAILED')),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_photos_album_status ON photos (album_id, index_status);

CREATE TABLE photo_embeddings (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  photo_id          UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
  vector_store_id   TEXT NOT NULL,
  modality          TEXT NOT NULL
                    CHECK (modality IN ('FACE', 'BODY', 'OUTFIT', 'SCENE')),
  model_name        TEXT NOT NULL,
  model_version     TEXT NOT NULL,
  index_partition   TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'READY'
                    CHECK (status IN ('READY', 'CLEANUP_PENDING', 'DELETED', 'FAILED')),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (vector_store_id, modality, model_version)
);

CREATE INDEX idx_embeddings_cleanup ON photo_embeddings (status, index_partition)
  WHERE status IN ('CLEANUP_PENDING', 'FAILED');

CREATE TABLE search_sessions (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id        UUID NOT NULL REFERENCES search_requests(id),
  actor_user_id     UUID NOT NULL REFERENCES users(id),
  consent_version   TEXT NOT NULL,
  query_text_cipher BYTEA,
  sort_mode         TEXT NOT NULL DEFAULT 'OVERALL'
                    CHECK (sort_mode IN ('OVERALL', 'FACE', 'OUTFIT', 'SCENE')),
  diversity_enabled BOOLEAN NOT NULL DEFAULT true,
  status            TEXT NOT NULL DEFAULT 'QUEUED'
                    CHECK (status IN ('QUEUED', 'EXTRACTING', 'SEARCHING', 'COMPLETED', 'FAILED', 'EXPIRED', 'REVOKED')),
  model_version     TEXT,
  experiment_key    TEXT,
  sample_object_key TEXT,
  sample_expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '24 hours'),
  p95_latency_ms    INTEGER,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at      TIMESTAMPTZ
);

CREATE INDEX idx_sessions_sample_cleanup
  ON search_sessions (sample_expires_at)
  WHERE sample_object_key IS NOT NULL;

CREATE TABLE search_results (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id        UUID NOT NULL REFERENCES search_sessions(id) ON DELETE CASCADE,
  photo_id          UUID NOT NULL REFERENCES photos(id),
  rank              INTEGER NOT NULL CHECK (rank > 0),
  overall_score     NUMERIC(5,4) NOT NULL CHECK (overall_score BETWEEN 0 AND 1),
  face_score        NUMERIC(5,4) CHECK (face_score BETWEEN 0 AND 1),
  outfit_score      NUMERIC(5,4) CHECK (outfit_score BETWEEN 0 AND 1),
  scene_score       NUMERIC(5,4) CHECK (scene_score BETWEEN 0 AND 1),
  explanation_json  JSONB NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, photo_id),
  UNIQUE (session_id, rank)
);

CREATE TABLE feedback_events (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  result_id         UUID NOT NULL REFERENCES search_results(id),
  actor_user_id     UUID NOT NULL REFERENCES users(id),
  relevance         TEXT NOT NULL CHECK (relevance IN ('RELEVANT', 'IRRELEVANT', 'UNDO')),
  reason_code       TEXT CHECK (reason_code IN ('WRONG_PERSON', 'WRONG_SCENE', 'WRONG_OUTFIT', 'PRIVACY_CONCERN', 'OTHER')),
  retention_until   TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '30 days'),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (reason_code IS NOT NULL OR relevance <> 'IRRELEVANT')
);

CREATE INDEX idx_feedback_retention ON feedback_events (retention_until);

CREATE TABLE audit_events (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id     UUID REFERENCES users(id),
  action            TEXT NOT NULL,
  resource_type     TEXT NOT NULL,
  resource_id       UUID,
  policy_version    TEXT NOT NULL,
  metadata          JSONB NOT NULL DEFAULT '{}'::jsonb,
  occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_resource ON audit_events (resource_type, resource_id, occurred_at DESC);

CREATE TABLE deletion_jobs (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  consent_id        UUID REFERENCES consent_records(id),
  resource_type     TEXT NOT NULL CHECK (resource_type IN ('SAMPLE', 'VECTOR', 'FEEDBACK', 'ACCOUNT')),
  resource_id       TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  attempts          INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  last_error        TEXT,
  due_at            TIMESTAMPTZ NOT NULL,
  completed_at      TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_deletion_jobs_due
  ON deletion_jobs (due_at, status)
  WHERE status IN ('PENDING', 'FAILED');

-- Application queries must always receive actor_user_id from verified JWT claims.
-- Production deployment should add PostgreSQL RLS policies for direct DB access,
-- short-lived signed storage URLs, encryption-at-rest and field-level encryption
-- for any optional free-text query stored for troubleshooting.
