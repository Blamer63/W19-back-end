-- =============================================================
-- Schema patches (idempotent — safe to re-run on every boot)
-- =============================================================
-- Notification center table. Production does not run data.sql; keep the
-- matching manual production script in docs/sql/create_notifications_table.sql.
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES profiles(id),
    actor_id UUID REFERENCES profiles(id),
    type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    target_url VARCHAR(255),
    read_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications (recipient_id, created_at);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read
    ON notifications (recipient_id, read_at);

UPDATE notifications SET type = 'POST_REACTION' WHERE type = 'POST_LIKE';

-- Allow image-only messages: content can be NULL when imageUrl is set.
-- ddl-auto:update never removes NOT NULL, so we do it here once.
ALTER TABLE messages ALTER COLUMN content DROP NOT NULL;

-- Group chat columns: ddl-auto:update cannot ADD COLUMN NOT NULL without a DEFAULT
-- on a table that already has rows. We add them here idempotently.
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS is_group BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS group_name VARCHAR(255);
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS group_avatar VARCHAR(255);

CREATE TABLE IF NOT EXISTS post_images (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    image_url VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_post_images_post_position
    ON post_images (post_id, position);

-- =============================================================
-- W19 LOCALE APP - SEED DATA
-- All demo users have password: demo123
-- UUIDs are fixed so cross-table references are stable
-- All inserts use ON CONFLICT DO NOTHING (idempotent)
-- =============================================================

-- -------------------------------------------------------
-- STEP 1: Languages
-- -------------------------------------------------------
INSERT INTO languages (code, name, native_name, flag_emoji) VALUES
('en', 'English',    'English',    '🇺🇸'),
('es', 'Spanish',    'Español',    '🇪🇸'),
('fr', 'French',     'Français',   '🇫🇷'),
('ja', 'Japanese',   '日本語',      '🇯🇵'),
('pt', 'Portuguese', 'Português',  '🇵🇹'),
('ko', 'Korean',     '한국어',      '🇰🇷'),
('vi', 'Vietnamese', 'Tiếng Việt', '🇻🇳')
ON CONFLICT (code) DO NOTHING;


-- -------------------------------------------------------
-- STEP 2: Profiles (Users)
-- Password hash for "demo123":
--   $2a$10$DbTwm5BsRd1G9ABkQ7yEbeAiyyPrkiaVoXNuiwH943iCnsVy6sv3S
-- -------------------------------------------------------
INSERT INTO profiles (id, username, email, password_hash, display_name, location, latitude, longitude, bio,
                      location_visibility, show_activity, show_saved_words, followers_count, following_count,
                      created_at, updated_at) VALUES
-- Minso (Korean native, learning English)
('10000000-0000-0000-0000-000000000001', 'minso_k', 'minso@locale.app',
 '$2a$10$DbTwm5BsRd1G9ABkQ7yEbeAiyyPrkiaVoXNuiwH943iCnsVy6sv3S',
 'Minso Kim', 'Seoul, South Korea', 37.5665, 126.9780,
 'Hello! I am a Korean native looking to improve my English. Coffee lover ☕',
 'PUBLIC', true, false, 3, 2, NOW(), NOW()),

-- Emma (English native, learning Korean & French)
('10000000-0000-0000-0000-000000000002', 'emma_uk', 'emma@locale.app',
 '$2a$10$DbTwm5BsRd1G9ABkQ7yEbeAiyyPrkiaVoXNuiwH943iCnsVy6sv3S',
 'Emma Smith', 'London, UK', 51.5074, -0.1278,
 'Learning Korean and French! Big fan of K-dramas 🎬',
 'PUBLIC', true, true, 2, 3, NOW(), NOW()),

-- Linh (Vietnamese native, learning English)
('10000000-0000-0000-0000-000000000003', 'linh_vn', 'linh@locale.app',
 '$2a$10$DbTwm5BsRd1G9ABkQ7yEbeAiyyPrkiaVoXNuiwH943iCnsVy6sv3S',
 'Linh Nguyen', 'Hanoi, Vietnam', 21.0285, 105.8542,
 'Native Vietnamese, improving my English. Love street food 🍜',
 'FRIENDS_ONLY', true, false, 1, 1, NOW(), NOW()),

-- Wei (Japanese native, learning English & Korean)
('10000000-0000-0000-0000-000000000004', 'wei_cn', 'wei@locale.app',
 '$2a$10$DbTwm5BsRd1G9ABkQ7yEbeAiyyPrkiaVoXNuiwH943iCnsVy6sv3S',
 'Wei Chen', 'Tokyo, Japan', 35.6762, 139.6503,
 'Always happy to chat and practice languages. 東京在住 🇯🇵',
 'PUBLIC', true, true, 2, 2, NOW(), NOW()),

-- Demo Account (Generic)
('10000000-0000-0000-0000-000000000005', 'demo_user', 'demo@locale.app',
 '$2a$10$DbTwm5BsRd1G9ABkQ7yEbeAiyyPrkiaVoXNuiwH943iCnsVy6sv3S',
 'Demo User', 'San Francisco, CA', 37.7749, -122.4194,
 'I am just looking around! 👋',
 'PUBLIC', true, false, 0, 0, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;


-- -------------------------------------------------------
-- STEP 3: User Roles
-- -------------------------------------------------------
INSERT INTO user_roles (id, user_id, role) VALUES
('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'USER'),
('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'USER'),
('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'USER'),
('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 'USER'),
('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000005', 'USER')
ON CONFLICT DO NOTHING;



-- -------------------------------------------------------
-- STEP 4: User Languages (native + learning)
-- -------------------------------------------------------
INSERT INTO user_languages (id, profile_id, language_code, proficiency, is_learning, created_at, updated_at) VALUES
-- Minso: native Korean, learning English (intermediate)
('50000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'ko', 'NATIVE',       false, NOW(), NOW()),
('50000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'en', 'INTERMEDIATE', true,  NOW(), NOW()),

-- Emma: native English, learning Korean (beginner) + French (beginner)
('50000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', 'en', 'NATIVE',       false, NOW(), NOW()),
('50000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 'ko', 'BEGINNER',     true,  NOW(), NOW()),
('50000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000002', 'fr', 'BEGINNER',     true,  NOW(), NOW()),

-- Linh: native Vietnamese, learning English (intermediate)
('50000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000003', 'vi', 'NATIVE',       false, NOW(), NOW()),
('50000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000003', 'en', 'INTERMEDIATE', true,  NOW(), NOW()),

-- Wei: native Japanese, learning English (advanced) + Korean (beginner)
('50000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000004', 'ja', 'NATIVE',       false, NOW(), NOW()),
('50000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000004', 'en', 'ADVANCED',     true,  NOW(), NOW()),
('50000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000004', 'ko', 'BEGINNER',     true,  NOW(), NOW())
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 5: User Settings (notifications + privacy + theme)
-- -------------------------------------------------------
INSERT INTO user_settings (id, profile_id,
    notify_push, notify_email, notify_likes, notify_comments, notify_meetups,
    privacy_location_visibility, privacy_allow_messages,
    theme, created_at, updated_at) VALUES
('60000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
 true, true, true, true, true, 'PUBLIC', 'everyone', 'system', NOW(), NOW()),
('60000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
 true, false, true, true, true, 'PUBLIC', 'everyone', 'light',  NOW(), NOW()),
('60000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003',
 true, true, false, true, true, 'FRIENDS_ONLY', 'friends', 'system', NOW(), NOW()),
('60000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004',
 false, false, true, false, true, 'PUBLIC', 'everyone', 'dark',  NOW(), NOW())
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 6: Friends
-- Minso ↔ Emma: ACCEPTED
-- Minso → Linh: PENDING
-- Wei ↔ Emma: ACCEPTED
-- -------------------------------------------------------
INSERT INTO friends (id, requester_id, receiver_id, status, created_at, updated_at) VALUES
('70000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'ACCEPTED', NOW(), NOW()),
('70000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003', 'PENDING',  NOW(), NOW()),
('70000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 'ACCEPTED', NOW(), NOW())
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 7: Posts
-- -------------------------------------------------------
INSERT INTO posts (id, author_id, content, original_language, latitude, longitude, status, created_at, updated_at) VALUES
-- Minso posts
('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
 '안녕하세요! 영어를 연습하고 싶습니다. 같이 커피 마실 사람 있나요?', 'ko', 37.5665, 126.9780, 'ACTIVE', NOW() - INTERVAL '5 days', NOW()),
('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
 'I went to a great cafe today in Gangnam. Amazing latte art! ☕', 'en', 37.5675, 126.9800, 'ACTIVE', NOW() - INTERVAL '2 days', NOW()),

-- Emma posts
('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002',
 'Visiting London Tower today! Does anyone here speak French? I need practice partner 😅', 'en', 51.5080, -0.0763, 'ACTIVE', NOW() - INTERVAL '3 days', NOW()),

-- Linh posts
('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000003',
 'Phở is the best food in the world. Không thể phủ nhận! 🍜', 'vi', 21.0285, 105.8542, 'ACTIVE', NOW() - INTERVAL '1 day', NOW()),

-- Wei posts
('30000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000004',
 '今日は天気がよくて、東京を散歩しました。Have you ever visited Tokyo?', 'ja', 35.6762, 139.6503, 'ACTIVE', NOW() - INTERVAL '4 hours', NOW())
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 8: Post Translations
-- -------------------------------------------------------
INSERT INTO post_translations (id, post_id, language_code, translated_content, created_at, updated_at) VALUES
('80000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'en',
 'Hello! I want to practice English. Anyone want to grab coffee together?', NOW(), NOW()),
('80000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000004', 'en',
 'Pho is the best food in the world. Can''t deny it! 🍜', NOW(), NOW()),
('80000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000005', 'en',
 'Great weather today, walking through Tokyo. Have you ever visited Tokyo?', NOW(), NOW())
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 9: Post Reactions
-- -------------------------------------------------------
INSERT INTO post_reactions (post_id, profile_id, type) VALUES
-- Emma likes Minso's Korean post
('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'LIKE'),
-- Wei loves Minso's cafe post
('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004', 'LOVE'),
-- Minso likes Emma's London post
('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'LIKE'),
-- Emma marks Wei's post as helpful
('30000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000002', 'HELPFUL'),
-- Linh finds Minso's cafe post funny
('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003', 'FUNNY')
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 10: Post Comments
-- -------------------------------------------------------
INSERT INTO post_comments (id, post_id, author_id, parent_comment_id, content, created_at, updated_at) VALUES
-- Emma comments on Minso's Korean intro post
('90000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002',
 NULL, 'Hi Minso! I would love to practice together. I am learning Korean too 😊', NOW() - INTERVAL '4 days', NOW()),
-- Minso replies to Emma
('90000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
 '90000000-0000-0000-0000-000000000001', 'That is great Emma! Let us set up a meetup 🙌', NOW() - INTERVAL '3 days', NOW()),
-- Wei comments on Emma's London post
('90000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004',
 NULL, 'I speak French! Je peux t''aider à pratiquer 😄', NOW() - INTERVAL '2 days', NOW()),
-- Linh comments on Wei's Beijing post
('90000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000003',
 NULL, 'Beijing looks beautiful! I want to visit someday 🌸', NOW() - INTERVAL '3 hours', NOW())
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 11: Meetups
-- -------------------------------------------------------
INSERT INTO meetups (id, organizer_id, title, description, language_code, meetup_date, location,
                     latitude, longitude, max_attendees, status, created_at, updated_at) VALUES
('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
 'Seoul Weekend Language Exchange',
 'Let''s practice English and Korean over coffee! All levels welcome.',
 'ko', NOW() + INTERVAL '3 days', 'Gangnam Station Cafe 2', 37.4979, 127.0276, 10, 'UPCOMING', NOW(), NOW()),

('40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003',
 'Hanoi Street Food & English Chat',
 'Learning English while eating Bún Chả. Let''s explore the Old Quarter!',
 'vi', NOW() + INTERVAL '1 week', 'Old Quarter, Hanoi', 21.0335, 105.8506, 5, 'UPCOMING', NOW(), NOW()),

('40000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004',
 'Tokyo Japanese Practice Session',
 'Open for all learners wanting to practice Japanese. ようこそ！',
 'ja', NOW() + INTERVAL '2 days', 'Shibuya, Tokyo', 35.6595, 139.7005, 8, 'UPCOMING', NOW(), NOW()),

('40000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002',
 'London Korean Study Group',
 'Weekly Korean study group for beginners. Textbooks provided!',
 'ko', NOW() + INTERVAL '5 days', 'British Library, London', 51.5298, -0.1272, 6, 'UPCOMING', NOW(), NOW())
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 12: Meetup Attendees
-- -------------------------------------------------------
INSERT INTO meetup_attendees (id, meetup_id, attendee_id, joined_at, created_at, updated_at) VALUES
-- Emma joins Minso's Seoul meetup
('a0000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001',
 '10000000-0000-0000-0000-000000000002', NOW() - INTERVAL '2 days', NOW(), NOW()),
-- Wei joins Minso's Seoul meetup
('a0000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000001',
 '10000000-0000-0000-0000-000000000004', NOW() - INTERVAL '1 day',  NOW(), NOW()),
-- Minso joins Emma's London study group (virtual interest)
('a0000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000004',
 '10000000-0000-0000-0000-000000000001', NOW() - INTERVAL '12 hours', NOW(), NOW()),
-- Emma joins Wei's Beijing session
('a0000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000003',
 '10000000-0000-0000-0000-000000000002', NOW() - INTERVAL '6 hours',  NOW(), NOW())
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 13: Conversations + Participants + Messages
-- -------------------------------------------------------

-- Conversation 1: Minso ↔ Emma
INSERT INTO conversations (id, is_group, last_message_preview, last_message_at, created_at, updated_at) VALUES
('b0000000-0000-0000-0000-000000000001',
 false, 'Sure! See you at the café this Saturday 😊', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '2 days', NOW())
ON CONFLICT DO NOTHING;

INSERT INTO conversation_participants (conversation_id, profile_id) VALUES
('b0000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001'),
('b0000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002')
ON CONFLICT DO NOTHING;

INSERT INTO messages (id, conversation_id, sender_id, content, is_read, created_at, updated_at) VALUES
('c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001',
 '10000000-0000-0000-0000-000000000001', 'Hi Emma! Saw your comment — want to do a language exchange? 😊', true, NOW() - INTERVAL '2 days', NOW()),
('c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001',
 '10000000-0000-0000-0000-000000000002', 'Yes! I would love that. When are you free this week?', true, NOW() - INTERVAL '2 days' + INTERVAL '5 minutes', NOW()),
('c0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000001',
 '10000000-0000-0000-0000-000000000001', 'Saturday morning works for me! There is a nice café in Gangnam.', true, NOW() - INTERVAL '1 day', NOW()),
('c0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000001',
 '10000000-0000-0000-0000-000000000002', 'Sure! See you at the café this Saturday 😊', false, NOW() - INTERVAL '1 hour', NOW())
ON CONFLICT DO NOTHING;

-- Conversation 2: Wei ↔ Emma
INSERT INTO conversations (id, is_group, last_message_preview, last_message_at, created_at, updated_at) VALUES
('b0000000-0000-0000-0000-000000000002',
 false, '谢谢！你的中文很好！', NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '1 day', NOW())
ON CONFLICT DO NOTHING;

INSERT INTO conversation_participants (conversation_id, profile_id) VALUES
('b0000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004'),
('b0000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002')
ON CONFLICT DO NOTHING;

INSERT INTO messages (id, conversation_id, sender_id, content, is_read, created_at, updated_at) VALUES
('c0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000002',
 '10000000-0000-0000-0000-000000000002', 'Hi Wei! I saw your offer to help with Japanese. Can we practice?', true, NOW() - INTERVAL '1 day', NOW()),
('c0000000-0000-0000-0000-000000000006', 'b0000000-0000-0000-0000-000000000002',
 '10000000-0000-0000-0000-000000000004', '当然可以！Let''s start. 你会说多少中文？', true, NOW() - INTERVAL '23 hours', NOW()),
('c0000000-0000-0000-0000-000000000007', 'b0000000-0000-0000-0000-000000000002',
 '10000000-0000-0000-0000-000000000002', '日本語を勉強するのが好きです！(I hope that means I like learning Japanese 😂)', true, NOW() - INTERVAL '22 hours', NOW()),
('c0000000-0000-0000-0000-000000000008', 'b0000000-0000-0000-0000-000000000002',
 '10000000-0000-0000-0000-000000000004', '谢谢！你的中文很好！', false, NOW() - INTERVAL '30 minutes', NOW())
ON CONFLICT DO NOTHING;


-- -------------------------------------------------------
-- STEP 14: Saved Words (for Minso and Emma)
-- -------------------------------------------------------
INSERT INTO saved_words (id, user_id, word, translation, language_code, source, mastery_level, next_review, created_at) VALUES
-- Minso's saved English words
('d0000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
 'serendipity', '우연한 행운', 'en', 'MANUAL', 2, NOW() + INTERVAL '3 days', NOW() - INTERVAL '5 days'),
('d0000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
 'eloquent', '유창한', 'en', 'POST', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '3 days'),
('d0000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001',
 'perseverance', '인내', 'en', 'MANUAL', 0, NOW(), NOW() - INTERVAL '1 day'),

-- Emma's saved Korean words
('d0000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002',
 '안녕하세요', 'Hello (formal)', 'ko', 'MANUAL', 5, NOW() + INTERVAL '14 days', NOW() - INTERVAL '10 days'),
('d0000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000002',
 '감사합니다', 'Thank you', 'ko', 'MANUAL', 4, NOW() + INTERVAL '7 days', NOW() - INTERVAL '7 days'),
('d0000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000002',
 '맛있어요', 'It is delicious', 'ko', 'POST', 2, NOW() + INTERVAL '2 days', NOW() - INTERVAL '4 days'),
('d0000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000002',
 '사랑해요', 'I love you', 'ko', 'MANUAL', 3, NOW() + INTERVAL '5 days', NOW() - INTERVAL '6 days'),

-- Wei's saved English words
('d0000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000004',
 'hutong', '胡同 (alley)', 'en', 'MANUAL', 3, NOW() + INTERVAL '4 days', NOW() - INTERVAL '8 days'),
('d0000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000004',
 'wanderlust', '旅行渴望', 'en', 'POST', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '2 days'),

-- More words for Minso
('d0000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000001',
 'ambiguous', '모호한', 'en', 'MANUAL', 0, NOW(), NOW() - INTERVAL '1 hour'),
('d0000000-0000-0000-0000-000000000011', '10000000-0000-0000-0000-000000000001',
 'inevitable', '불가피한', 'en', 'POST', 2, NOW() + INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('d0000000-0000-0000-0000-000000000012', '10000000-0000-0000-0000-000000000001',
 'conscientious', '양심적인', 'en', 'MANUAL', 3, NOW() + INTERVAL '5 days', NOW() - INTERVAL '6 days'),

-- More words for Emma
('d0000000-0000-0000-0000-000000000013', '10000000-0000-0000-0000-000000000002',
 '괜찮아요', 'It is okay', 'ko', 'POST', 4, NOW() + INTERVAL '7 days', NOW() - INTERVAL '8 days'),
('d0000000-0000-0000-0000-000000000014', '10000000-0000-0000-0000-000000000002',
 '미안해요', 'I am sorry', 'ko', 'MANUAL', 5, NOW() + INTERVAL '14 days', NOW() - INTERVAL '12 days'),
('d0000000-0000-0000-0000-000000000015', '10000000-0000-0000-0000-000000000002',
 '이름이 뭐예요?', 'What is your name?', 'ko', 'MANUAL', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '1 day'),

-- Words for Linh
('d0000000-0000-0000-0000-000000000016', '10000000-0000-0000-0000-000000000003',
 'vocabulary', 'từ vựng', 'en', 'MANUAL', 2, NOW() + INTERVAL '2 days', NOW() - INTERVAL '3 days'),
('d0000000-0000-0000-0000-000000000017', '10000000-0000-0000-0000-000000000003',
 'pronunciation', 'phát âm', 'en', 'POST', 0, NOW(), NOW() - INTERVAL '2 hours'),
('d0000000-0000-0000-0000-000000000018', '10000000-0000-0000-0000-000000000003',
 'fluent', 'trôi chảy', 'en', 'MANUAL', 3, NOW() + INTERVAL '4 days', NOW() - INTERVAL '5 days'),

-- More words for Wei
('d0000000-0000-0000-0000-000000000019', '10000000-0000-0000-0000-000000000004',
 'enthusiastic', '热情的', 'en', 'POST', 2, NOW() + INTERVAL '3 days', NOW() - INTERVAL '4 days'),
('d0000000-0000-0000-0000-000000000020', '10000000-0000-0000-0000-000000000004',
 'spontaneous', '自发的', 'en', 'MANUAL', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '1 day'),

-- Extra words for Minso (Korean native, learning English)
('d0000000-0000-0000-0000-000000000021', '10000000-0000-0000-0000-000000000001', 'procrastination', '미루는 버릇', 'en', 'MANUAL', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '2 days'),
('d0000000-0000-0000-0000-000000000022', '10000000-0000-0000-0000-000000000001', 'resilience', '회복력', 'en', 'POST', 2, NOW() + INTERVAL '2 days', NOW() - INTERVAL '3 days'),
('d0000000-0000-0000-0000-000000000023', '10000000-0000-0000-0000-000000000001', 'subtle', '미묘한', 'en', 'MANUAL', 3, NOW() + INTERVAL '3 days', NOW() - INTERVAL '4 days'),
('d0000000-0000-0000-0000-000000000024', '10000000-0000-0000-0000-000000000001', 'meticulous', '꼼꼼한', 'en', 'MANUAL', 0, NOW(), NOW() - INTERVAL '1 hour'),
('d0000000-0000-0000-0000-000000000025', '10000000-0000-0000-0000-000000000001', 'pragmatic', '실용적인', 'en', 'POST', 4, NOW() + INTERVAL '7 days', NOW() - INTERVAL '8 days'),

-- Extra words for Emma (English native, learning Korean)
('d0000000-0000-0000-0000-000000000026', '10000000-0000-0000-0000-000000000002', '주말', 'Weekend', 'ko', 'MANUAL', 2, NOW() + INTERVAL '2 days', NOW() - INTERVAL '3 days'),
('d0000000-0000-0000-0000-000000000027', '10000000-0000-0000-0000-000000000002', '친구', 'Friend', 'ko', 'POST', 3, NOW() + INTERVAL '4 days', NOW() - INTERVAL '5 days'),
('d0000000-0000-0000-0000-000000000028', '10000000-0000-0000-0000-000000000002', '도서관', 'Library', 'ko', 'MANUAL', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '1 day'),
('d0000000-0000-0000-0000-000000000029', '10000000-0000-0000-0000-000000000002', '약속', 'Appointment / Promise', 'ko', 'MANUAL', 0, NOW(), NOW() - INTERVAL '2 hours'),
('d0000000-0000-0000-0000-000000000030', '10000000-0000-0000-0000-000000000002', '지하철', 'Subway', 'ko', 'POST', 4, NOW() + INTERVAL '6 days', NOW() - INTERVAL '7 days'),

-- Extra words for Linh (Vietnamese native, learning English)
('d0000000-0000-0000-0000-000000000031', '10000000-0000-0000-0000-000000000003', 'delicious', 'ngon', 'en', 'MANUAL', 5, NOW() + INTERVAL '14 days', NOW() - INTERVAL '14 days'),
('d0000000-0000-0000-0000-000000000032', '10000000-0000-0000-0000-000000000003', 'journey', 'hành trình', 'en', 'POST', 2, NOW() + INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('d0000000-0000-0000-0000-000000000033', '10000000-0000-0000-0000-000000000003', 'experience', 'kinh nghiệm', 'en', 'MANUAL', 3, NOW() + INTERVAL '4 days', NOW() - INTERVAL '5 days'),
('d0000000-0000-0000-0000-000000000034', '10000000-0000-0000-0000-000000000003', 'knowledge', 'kiến thức', 'en', 'MANUAL', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '1 day'),
('d0000000-0000-0000-0000-000000000035', '10000000-0000-0000-0000-000000000003', 'challenging', 'thử thách', 'en', 'POST', 0, NOW(), NOW() - INTERVAL '1 hour'),
('d0000000-0000-0000-0000-000000000036', '10000000-0000-0000-0000-000000000003', 'rewarding', 'đáng giá', 'en', 'MANUAL', 4, NOW() + INTERVAL '7 days', NOW() - INTERVAL '8 days'),
('d0000000-0000-0000-0000-000000000037', '10000000-0000-0000-0000-000000000003', 'consistent', 'nhất quán', 'en', 'MANUAL', 2, NOW() + INTERVAL '3 days', NOW() - INTERVAL '4 days'),
('d0000000-0000-0000-0000-000000000038', '10000000-0000-0000-0000-000000000003', 'improvement', 'sự cải thiện', 'en', 'POST', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '2 days'),

-- Extra words for Wei (Japanese native, learning English)
('d0000000-0000-0000-0000-000000000039', '10000000-0000-0000-0000-000000000004', 'fascinating', '迷人的', 'en', 'MANUAL', 3, NOW() + INTERVAL '5 days', NOW() - INTERVAL '6 days'),
('d0000000-0000-0000-0000-000000000040', '10000000-0000-0000-0000-000000000004', 'architecture', '建筑', 'en', 'POST', 2, NOW() + INTERVAL '2 days', NOW() - INTERVAL '3 days'),
('d0000000-0000-0000-0000-000000000041', '10000000-0000-0000-0000-000000000004', 'tradition', '传统', 'en', 'MANUAL', 4, NOW() + INTERVAL '7 days', NOW() - INTERVAL '8 days'),
('d0000000-0000-0000-0000-000000000042', '10000000-0000-0000-0000-000000000004', 'authentic', '正宗的', 'en', 'MANUAL', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '1 day'),
('d0000000-0000-0000-0000-000000000043', '10000000-0000-0000-0000-000000000004', 'atmosphere', '气氛', 'en', 'POST', 0, NOW(), NOW() - INTERVAL '4 hours'),
('d0000000-0000-0000-0000-000000000044', '10000000-0000-0000-0000-000000000004', 'heritage', '遗产', 'en', 'MANUAL', 2, NOW() + INTERVAL '3 days', NOW() - INTERVAL '4 days'),
('d0000000-0000-0000-0000-000000000045', '10000000-0000-0000-0000-000000000004', 'cuisine', '烹饪', 'en', 'MANUAL', 5, NOW() + INTERVAL '14 days', NOW() - INTERVAL '15 days'),

-- Words for Demo User (Generic, English native, learning Spanish - oh wait we don't have Spanish. Let's say learning Korean)
('d0000000-0000-0000-0000-000000000046', '10000000-0000-0000-0000-000000000005', '안녕하세요', 'Hello', 'ko', 'MANUAL', 3, NOW() + INTERVAL '3 days', NOW() - INTERVAL '4 days'),
('d0000000-0000-0000-0000-000000000047', '10000000-0000-0000-0000-000000000005', '감사합니다', 'Thank you', 'ko', 'POST', 2, NOW() + INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('d0000000-0000-0000-0000-000000000048', '10000000-0000-0000-0000-000000000005', '죄송합니다', 'Sorry', 'ko', 'MANUAL', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '1 day'),
('d0000000-0000-0000-0000-000000000049', '10000000-0000-0000-0000-000000000005', '네', 'Yes', 'ko', 'MANUAL', 5, NOW() + INTERVAL '14 days', NOW() - INTERVAL '15 days'),
('d0000000-0000-0000-0000-000000000050', '10000000-0000-0000-0000-000000000005', '아니요', 'No', 'ko', 'POST', 4, NOW() + INTERVAL '7 days', NOW() - INTERVAL '8 days'),
('d0000000-0000-0000-0000-000000000051', '10000000-0000-0000-0000-000000000005', '부탁합니다', 'Please', 'ko', 'MANUAL', 0, NOW(), NOW() - INTERVAL '2 hours'),
('d0000000-0000-0000-0000-000000000052', '10000000-0000-0000-0000-000000000005', '어디에요?', 'Where is it?', 'ko', 'MANUAL', 2, NOW() + INTERVAL '3 days', NOW() - INTERVAL '4 days'),
('d0000000-0000-0000-0000-000000000053', '10000000-0000-0000-0000-000000000005', '얼마에요?', 'How much is it?', 'ko', 'POST', 1, NOW() + INTERVAL '1 day', NOW() - INTERVAL '2 days'),
('d0000000-0000-0000-0000-000000000054', '10000000-0000-0000-0000-000000000005', '도와주세요', 'Help me', 'ko', 'MANUAL', 3, NOW() + INTERVAL '5 days', NOW() - INTERVAL '6 days'),
('d0000000-0000-0000-0000-000000000055', '10000000-0000-0000-0000-000000000005', '음식', 'Food', 'ko', 'MANUAL', 5, NOW() + INTERVAL '20 days', NOW() - INTERVAL '21 days'),
('d0000000-0000-0000-0000-000000000056', '10000000-0000-0000-0000-000000000005', '물', 'Water', 'ko', 'POST', 4, NOW() + INTERVAL '8 days', NOW() - INTERVAL '9 days')
ON CONFLICT DO NOTHING;

-- Inject language for demo user
INSERT INTO user_languages (id, profile_id, language_code, proficiency, is_learning, created_at, updated_at) VALUES
('50000000-0000-0000-0000-000000000011', '10000000-0000-0000-0000-000000000005', 'en', 'NATIVE', false, NOW(), NOW()),
('50000000-0000-0000-0000-000000000012', '10000000-0000-0000-0000-000000000005', 'ko', 'BEGINNER', true, NOW(), NOW())
ON CONFLICT DO NOTHING;

