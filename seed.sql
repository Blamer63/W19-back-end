-- STEP 1: Insert Supported Languages
INSERT INTO languages (code, name, native_name, flag_emoji) VALUES
('en', 'English', 'English', '🇺🇸'),
('ko', 'Korean', '한국어', '🇰🇷'),
('vi', 'Vietnamese', 'Tiếng Việt', '🇻🇳'),
('zh', 'Chinese', '中文', '🇨🇳')
ON CONFLICT (code) DO NOTHING;

-- STEP 2: Insert Profiles (Users)
-- Password for all is "demo123" (using bcrypt hash: $2a$10$ug/Ttw0rUHF2XMvexo3LUuaBMuU/exNThldLGGFyAotbPAD/jdZTqu )
-- We use fixed UUIDs so we can reference them in posts and meetups

-- Minso (Korean)
INSERT INTO profiles (id, username, email, password_hash, display_name, location, latitude, longitude, bio, show_activity, show_saved_words, created_at, updated_at) VALUES 
('10000000-0000-0000-0000-000000000001', 'minso_k', 'minso@locale.app', '$2a$10$ug/Ttw0rUHF2XMvexo3LUuaBMuU/exNThldLGGFyAotbPAD/jdZTqu', 'Minso Kim', 'Seoul, South Korea', 37.5665, 126.9780, 'Hello! I am looking to practice my English.', true, false, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Emma (English)
INSERT INTO profiles (id, username, email, password_hash, display_name, location, latitude, longitude, bio, show_activity, show_saved_words, created_at, updated_at) VALUES 
('10000000-0000-0000-0000-000000000002', 'emma_uk', 'emma@locale.app', '$2a$10$ug/Ttw0rUHF2XMvexo3LUuaBMuU/exNThldLGGFyAotbPAD/jdZTqu', 'Emma Smith', 'London, UK', 51.5074, -0.1278, 'Learning Korean and Chinese!', true, false, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Linh (Vietnamese)
INSERT INTO profiles (id, username, email, password_hash, display_name, location, latitude, longitude, bio, show_activity, show_saved_words, created_at, updated_at) VALUES 
('10000000-0000-0000-0000-000000000003', 'linh_vn', 'linh@locale.app', '$2a$10$ug/Ttw0rUHF2XMvexo3LUuaBMuU/exNThldLGGFyAotbPAD/jdZTqu', 'Linh Nguyen', 'Hanoi, Vietnam', 21.0285, 105.8542, 'Native Vietnamese, improving my English.', true, false, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Wei (Chinese)
INSERT INTO profiles (id, username, email, password_hash, display_name, location, latitude, longitude, bio, show_activity, show_saved_words, created_at, updated_at) VALUES 
('10000000-0000-0000-0000-000000000004', 'wei_cn', 'wei@locale.app', '$2a$10$ug/Ttw0rUHF2XMvexo3LUuaBMuU/exNThldLGGFyAotbPAD/jdZTqu', 'Wei Chen', 'Beijing, China', 39.9042, 116.4074, 'Always happy to chat and practice languages.', true, false, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;


-- STEP 3: Assign User Roles
INSERT INTO user_roles (id, user_id, role) VALUES
('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'USER'),
('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'USER'),
('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'USER'),
('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 'USER')
ON CONFLICT DO NOTHING;


-- STEP 4: Insert Posts
-- Minso posts
INSERT INTO posts (id, author_id, content, original_language, latitude, longitude, status, created_at, updated_at) VALUES 
('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '안녕하세요! 영어를 연습하고 싶습니다.', 'ko', 37.5665, 126.9780, 'ACTIVE', NOW(), NOW()),
('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'I went to a great cafe today in Seoul.', 'en', 37.5675, 126.9800, 'ACTIVE', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Emma posts
INSERT INTO posts (id, author_id, content, original_language, latitude, longitude, status, created_at, updated_at) VALUES 
('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', 'Visiting London tower! Does anyone here speak Chinese?', 'en', 51.5080, -0.0763, 'ACTIVE', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Linh posts
INSERT INTO posts (id, author_id, content, original_language, latitude, longitude, status, created_at, updated_at) VALUES 
('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000003', 'Phở is the best food in the world.', 'vi', 21.0285, 105.8542, 'ACTIVE', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Wei posts
INSERT INTO posts (id, author_id, content, original_language, latitude, longitude, status, created_at, updated_at) VALUES 
('30000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000004', '今天天气很好。', 'zh', 39.9042, 116.4074, 'ACTIVE', NOW(), NOW())
ON CONFLICT DO NOTHING;


-- STEP 5: Insert Meetups
INSERT INTO meetups (id, organizer_id, title, description, language_code, meetup_date, location, latitude, longitude, max_attendees, status, created_at, updated_at) VALUES 
('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Seoul Weekend Exchange', 'Let''s practice English and Korean over coffee!', 'ko', NOW() + INTERVAL '3 days', 'Gangnam Station Cafe 2', 37.4979, 127.0276, 10, 'UPCOMING', NOW(), NOW()),
('40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003', 'Hanoi Street Food & Chat', 'Learning English while eating Bun Cha.', 'vi', NOW() + INTERVAL '1 week', 'Old Quarter, Hanoi', 21.0335, 105.8506, 5, 'UPCOMING', NOW(), NOW()),
('40000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004', 'Beijing Chinese Practice', 'Open for all foreigners to practice Chinese.', 'zh', NOW() + INTERVAL '2 days', 'Sanlitun, Beijing', 39.9329, 116.4477, 8, 'UPCOMING', NOW(), NOW())
ON CONFLICT DO NOTHING;
