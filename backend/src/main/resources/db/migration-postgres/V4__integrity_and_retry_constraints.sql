-- Complete the relational integrity introduced by the core workflow and
-- protect cleanup jobs from duplicate retries.
ALTER TABLE search_results ADD CONSTRAINT fk_result_album
  FOREIGN KEY (album_id) REFERENCES albums(id);

ALTER TABLE feedback_events ADD CONSTRAINT fk_feedback_session
  FOREIGN KEY (session_id) REFERENCES search_sessions(id) ON DELETE CASCADE;
ALTER TABLE feedback_events ADD CONSTRAINT fk_feedback_result
  FOREIGN KEY (result_id) REFERENCES search_results(id) ON DELETE CASCADE;
ALTER TABLE feedback_events ADD CONSTRAINT fk_feedback_actor
  FOREIGN KEY (actor_user_id) REFERENCES users(id);
ALTER TABLE feedback_events ADD CONSTRAINT ck_feedback_relevance
  CHECK (relevance IN ('RELEVANT', 'IRRELEVANT', 'UNDO'));

ALTER TABLE deletion_jobs ADD CONSTRAINT uq_deletion_consent_resource
  UNIQUE (consent_id, resource_type, resource_id);
ALTER TABLE deletion_jobs ADD CONSTRAINT ck_deletion_attempts
  CHECK (attempts >= 0);
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_attempts
  CHECK (attempts >= 0);
