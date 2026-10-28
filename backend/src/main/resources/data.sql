MERGE INTO users (id, display_name, role) KEY(id) VALUES
  ('u-participant', '林岚', 'PARTICIPANT'),
  ('u-owner', '林澜', 'OWNER'),
  ('u-admin', '系统管理员', 'ADMIN');

MERGE INTO albums (id, owner_user_id, name, photo_count, index_status) KEY(id) VALUES
  ('album-cabin', 'u-owner', '山间木屋生日会', 586, 'READY'),
  ('album-camping', 'u-owner', '春日露营', 412, 'READY'),
  ('album-city', 'u-owner', '城市漫步', 250, 'READY');

MERGE INTO album_members (album_id, user_id, role, status) KEY(album_id, user_id) VALUES
  ('album-cabin', 'u-owner', 'OWNER', 'ACTIVE'),
  ('album-cabin', 'u-participant', 'PARTICIPANT', 'ACTIVE'),
  ('album-camping', 'u-owner', 'OWNER', 'ACTIVE'),
  ('album-camping', 'u-participant', 'PARTICIPANT', 'ACTIVE'),
  ('album-city', 'u-owner', 'OWNER', 'ACTIVE'),
  ('album-city', 'u-participant', 'PARTICIPANT', 'ACTIVE');

MERGE INTO search_requests (
  id, album_id, requester_user_id, purpose_code, status, requested_days,
  created_at
) KEY(id) VALUES (
  'request-seed-pending', 'album-cabin', 'u-participant',
  'FIND_MY_PHOTOS', 'REQUESTED', 7, CURRENT_TIMESTAMP
);
