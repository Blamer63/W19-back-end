-- Manual repair for saved-word starter data in production-like databases.
-- Local development runs the same logic from src/main/resources/data.sql.

ALTER TABLE saved_words ADD COLUMN IF NOT EXISTS topic VARCHAR(255);

ALTER TABLE saved_words DROP CONSTRAINT IF EXISTS saved_words_source_check;
ALTER TABLE saved_words ADD CONSTRAINT saved_words_source_check CHECK (source IN (
    'MANUAL',
    'POST',
    'SCANNER',
    'STARTER',
    'AR_SCAN',
    'CHAT'
));

WITH starter_seed_contexts(context, topic) AS (
    VALUES
        ('Start a casual conversation.', 'greetings'),
        ('A casual greeting.', 'greetings'),
        ('Start a daytime conversation.', 'greetings'),
        ('A friendly daytime greeting.', 'greetings'),
        ('Use after someone helps you.', 'greetings'),
        ('Use after receiving help.', 'greetings'),
        ('Useful after ordering.', 'greetings'),
        ('Use while ordering.', 'greetings'),
        ('Polite requests.', 'greetings'),
        ('Ordering politely.', 'greetings'),
        ('Useful on a grocery walk.', 'shopping'),
        ('Food shopping practice.', 'shopping'),
        ('Practise giving directions.', 'travel'),
        ('Directions practice.', 'travel'),
        ('Ask for directions.', 'travel'),
        ('Transport signs.', 'travel'),
        ('Planning a meetup.', 'travel'),
        ('Planning practice.', 'travel'),
        ('Read labels or order a drink.', 'food'),
        ('Ordering drinks.', 'food'),
        ('Cafe practice.', 'food')
)
UPDATE saved_words sw
SET source = 'STARTER',
    topic = COALESCE(sw.topic, starter_seed_contexts.topic)
FROM starter_seed_contexts
WHERE sw.source IN ('MANUAL', 'STARTER')
  AND sw.context = starter_seed_contexts.context;

WITH retired_japanese_romaji(old_word, native_word, translation, context, topic) AS (
    VALUES
        ('konnichiwa', 'こんにちは', 'hello', 'A friendly daytime greeting.', 'greetings'),
        ('arigato', 'ありがとう', 'thank you', 'Useful after ordering.', 'greetings'),
        ('ichiba', '市場', 'market', 'Food shopping practice.', 'shopping'),
        ('hidari', '左', 'left', 'Directions practice.', 'travel'),
        ('migi', '右', 'right', 'Directions practice.', 'travel'),
        ('onegaishimasu', 'お願いします', 'please', 'Polite requests.', 'greetings'),
        ('eki', '駅', 'station', 'Transport signs.', 'travel'),
        ('mizu', '水', 'water', 'Ordering drinks.', 'food'),
        ('koohii', '珈琲', 'coffee', 'Cafe practice.', 'food'),
        ('ashita', '明日', 'tomorrow', 'Planning practice.', 'travel')
),
duplicate_pairs AS (
    SELECT romaji.id AS romaji_id,
           native.id AS native_id,
           romaji.mastery_level AS romaji_mastery_level,
           romaji.next_review AS romaji_next_review,
           retired_japanese_romaji.topic
    FROM saved_words romaji
    JOIN retired_japanese_romaji
      ON LOWER(romaji.word) = retired_japanese_romaji.old_word
    JOIN saved_words native
      ON native.user_id = romaji.user_id
     AND native.language_code = 'ja'
     AND native.word = retired_japanese_romaji.native_word
    WHERE romaji.language_code = 'ja'
      AND romaji.source IN ('MANUAL', 'STARTER')
      AND EXISTS (
          SELECT 1
          FROM user_languages ul
          WHERE ul.profile_id = romaji.user_id
            AND ul.language_code = 'ja'
            AND ul.is_learning = true
      )
)
UPDATE saved_words native
SET mastery_level = GREATEST(COALESCE(native.mastery_level, 0), COALESCE(duplicate_pairs.romaji_mastery_level, 0)),
    next_review = LEAST(COALESCE(native.next_review, duplicate_pairs.romaji_next_review), COALESCE(duplicate_pairs.romaji_next_review, native.next_review)),
    topic = COALESCE(native.topic, duplicate_pairs.topic)
FROM duplicate_pairs
WHERE native.id = duplicate_pairs.native_id;

WITH retired_japanese_romaji(old_word, native_word) AS (
    VALUES
        ('konnichiwa', 'こんにちは'),
        ('arigato', 'ありがとう'),
        ('ichiba', '市場'),
        ('hidari', '左'),
        ('migi', '右'),
        ('onegaishimasu', 'お願いします'),
        ('eki', '駅'),
        ('mizu', '水'),
        ('koohii', '珈琲'),
        ('ashita', '明日')
)
DELETE FROM saved_words romaji
USING retired_japanese_romaji
WHERE romaji.language_code = 'ja'
  AND LOWER(romaji.word) = retired_japanese_romaji.old_word
  AND romaji.source IN ('MANUAL', 'STARTER')
  AND EXISTS (
      SELECT 1
      FROM user_languages ul
      WHERE ul.profile_id = romaji.user_id
        AND ul.language_code = 'ja'
        AND ul.is_learning = true
  )
  AND EXISTS (
      SELECT 1
      FROM saved_words native
      WHERE native.user_id = romaji.user_id
        AND native.language_code = 'ja'
        AND native.word = retired_japanese_romaji.native_word
  );

WITH retired_japanese_romaji(old_word, native_word, translation, context, topic) AS (
    VALUES
        ('konnichiwa', 'こんにちは', 'hello', 'A friendly daytime greeting.', 'greetings'),
        ('arigato', 'ありがとう', 'thank you', 'Useful after ordering.', 'greetings'),
        ('ichiba', '市場', 'market', 'Food shopping practice.', 'shopping'),
        ('hidari', '左', 'left', 'Directions practice.', 'travel'),
        ('migi', '右', 'right', 'Directions practice.', 'travel'),
        ('onegaishimasu', 'お願いします', 'please', 'Polite requests.', 'greetings'),
        ('eki', '駅', 'station', 'Transport signs.', 'travel'),
        ('mizu', '水', 'water', 'Ordering drinks.', 'food'),
        ('koohii', '珈琲', 'coffee', 'Cafe practice.', 'food'),
        ('ashita', '明日', 'tomorrow', 'Planning practice.', 'travel')
)
UPDATE saved_words sw
SET word = retired_japanese_romaji.native_word,
    translation = retired_japanese_romaji.translation,
    context = COALESCE(sw.context, retired_japanese_romaji.context),
    topic = COALESCE(sw.topic, retired_japanese_romaji.topic),
    source = 'STARTER'
FROM retired_japanese_romaji
WHERE sw.language_code = 'ja'
  AND LOWER(sw.word) = retired_japanese_romaji.old_word
  AND sw.source IN ('MANUAL', 'STARTER')
  AND EXISTS (
      SELECT 1
      FROM user_languages ul
      WHERE ul.profile_id = sw.user_id
        AND ul.language_code = 'ja'
        AND ul.is_learning = true
  );

DELETE FROM saved_words sw
WHERE sw.source = 'STARTER'
  AND NOT EXISTS (
      SELECT 1
      FROM user_languages ul
      WHERE ul.profile_id = sw.user_id
        AND ul.language_code = sw.language_code
        AND ul.is_learning = true
  );
