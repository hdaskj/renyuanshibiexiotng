-- A claimed job has a lease. Workers can reclaim a RUNNING job after the
-- configured timeout instead of leaving it stuck forever after a crash.
ALTER TABLE deletion_jobs ADD COLUMN claimed_at TIMESTAMPTZ;

CREATE INDEX idx_deletion_claim
  ON deletion_jobs (status, claimed_at);
