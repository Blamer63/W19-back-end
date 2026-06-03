package com.example.demo.service;

import com.example.demo.entity.Conversation;
import com.example.demo.entity.Friend;
import com.example.demo.entity.Language;
import com.example.demo.entity.Meetup;
import com.example.demo.entity.MeetupAttendee;
import com.example.demo.entity.Message;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostComment;
import com.example.demo.entity.PostImage;
import com.example.demo.entity.PostReaction;
import com.example.demo.entity.Profile;
import com.example.demo.entity.SavedWord;
import com.example.demo.entity.UserLanguage;
import com.example.demo.enums.FriendStatus;
import com.example.demo.enums.LocationVisibility;
import com.example.demo.enums.MeetupStatus;
import com.example.demo.enums.PostStatus;
import com.example.demo.enums.ProficiencyLevel;
import com.example.demo.enums.ReactionType;
import com.example.demo.enums.SourceType;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.FriendRepository;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.MeetupAttendeeRepository;
import com.example.demo.repository.MeetupRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.PostCommentRepository;
import com.example.demo.repository.PostReactionRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.SavedWordRepository;
import com.example.demo.repository.UserLanguageRepository;
import com.example.demo.service.SeedImageResolverService.SeedImageSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoLearningSeedService {

    private static final Set<String> SUPPORTED_LANGUAGE_CODES = Set.of("en", "es", "fr", "ja", "ko", "pt", "vi");
    private static final Set<String> RETIRED_JAPANESE_ROMAJI_WORDS = Set.of(
            "konnichiwa",
            "arigato",
            "ichiba",
            "hidari",
            "migi",
            "onegaishimasu",
            "eki",
            "mizu",
            "koohii",
            "ashita");

    private static final List<String> AVATAR_URLS = List.of(
            "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?w=240&h=240&fit=crop&crop=faces",
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=240&h=240&fit=crop&crop=faces",
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=240&h=240&fit=crop&crop=faces");

    private static final List<SeedImageSource> POST_IMAGES = List.of(
            SeedImageSource.jpg("https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=900&h=600&fit=crop"),
            SeedImageSource.jpg("https://images.unsplash.com/photo-1488459716781-31db52582fe9?w=900&h=600&fit=crop"),
            SeedImageSource.jpg("https://images.unsplash.com/photo-1542051841857-5f90071e7989?w=900&h=600&fit=crop"),
            SeedImageSource.jpg("https://images.unsplash.com/photo-1506973035872-a4ec16b8e8d9?w=900&h=600&fit=crop"),
            SeedImageSource.jpg("https://images.unsplash.com/photo-1542838132-92c53300491e?w=900&h=600&fit=crop"));

    private final ProfileRepository profileRepository;
    private final LanguageRepository languageRepository;
    private final UserLanguageRepository userLanguageRepository;
    private final SavedWordRepository savedWordRepository;
    private final FriendRepository friendRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PostRepository postRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostCommentRepository postCommentRepository;
    private final MeetupRepository meetupRepository;
    private final MeetupAttendeeRepository meetupAttendeeRepository;
    private final SeedImageResolverService seedImageResolverService;

    @Transactional
    public void seedForLearningLanguages(Profile learner, List<String> languageCodes) {
        if (languageCodes == null || learner == null) {
            return;
        }

        languageCodes.stream()
                .map(this::normalize)
                .filter(SUPPORTED_LANGUAGE_CODES::contains)
                .distinct()
                .forEach(code -> languageRepository.findByCode(code)
                        .ifPresent(language -> seedLanguage(learner, language)));
    }

    private void seedLanguage(Profile learner, Language language) {
        log.info("starter-seed: seeding {} — peers", language.getCode());
        List<Profile> peerProfiles = demoPeers(language).stream()
                .map(peer -> createPeer(language, peer))
                .toList();

        log.info("starter-seed: seeding {} — friendships", language.getCode());
        seedFriendships(learner, peerProfiles);
        log.info("starter-seed: seeding {} — conversations", language.getCode());
        seedConversations(learner, peerProfiles, language);
        log.info("starter-seed: seeding {} — posts", language.getCode());
        seedPosts(peerProfiles, language);
        log.info("starter-seed: seeding {} — meetups", language.getCode());
        seedMeetups(peerProfiles, language);
        log.info("starter-seed: seeding {} — saved words", language.getCode());
        seedWords(learner, language);
        log.info("starter-seed: seeding {} — done", language.getCode());
    }

    private Profile createPeer(Language language, DemoPeer peer) {
        String username = "demo_" + language.getCode() + "_" + peer.usernameSlug();
        String email = username + "@locale.demo";

        Profile profile = profileRepository.findByEmail(email)
                .orElseGet(() -> Profile.builder()
                        .username(username)
                        .email(email)
                        .passwordHash("demo-seeded-profile")
                        .build());

        profile.setUsername(username);
        profile.setDisplayName(peer.displayName());
        profile.setAvatarUrl(peer.avatarUrl());
        profile.setBio(peer.bio());
        profile.setLatitude(peer.latitude());
        profile.setLongitude(peer.longitude());
        profile.setLocation("Sydney NSW");
        profile.setLocationVisibility(LocationVisibility.PUBLIC);
        profile = profileRepository.save(profile);

        ensurePeerLanguage(profile, language);
        return profile;
    }

    private void ensurePeerLanguage(Profile profile, Language language) {
        boolean exists = profile.getLanguages().stream()
                .anyMatch(userLanguage -> userLanguage.getLanguage() != null
                        && language.getCode().equals(userLanguage.getLanguage().getCode()));
        if (exists) {
            return;
        }

        UserLanguage userLanguage = UserLanguage.builder()
                .profile(profile)
                .language(language)
                .proficiency(ProficiencyLevel.NATIVE)
                .isLearning(false)
                .build();
        userLanguageRepository.save(userLanguage);
        profile.getLanguages().add(userLanguage);
    }

    private void seedFriendships(Profile learner, List<Profile> peers) {
        for (Profile peer : peers) {
            friendRepository.findBetween(learner.getId(), peer.getId())
                    .ifPresentOrElse(friend -> {
                        if (friend.getStatus() != FriendStatus.ACCEPTED) {
                            friend.setStatus(FriendStatus.ACCEPTED);
                            friendRepository.save(friend);
                        }
                    }, () -> friendRepository.save(Friend.builder()
                            .requester(peer)
                            .receiver(learner)
                            .status(FriendStatus.ACCEPTED)
                            .build()));
        }
    }

    private void seedConversations(Profile learner, List<Profile> peers, Language language) {
        List<DemoMessage> messages = demoMessages(language);
        for (int i = 0; i < peers.size() && i < messages.size(); i++) {
            Profile peer = peers.get(i);
            DemoMessage demo = messages.get(i);
            if (conversationRepository.findBetweenUsers(learner.getId(), peer.getId()).isPresent()) {
                continue;
            }

            Conversation conversation = Conversation.builder()
                    .isGroup(false)
                    .lastMessagePreview(demo.followUp())
                    .lastMessageAt(LocalDateTime.now().minusMinutes(30L + i * 18L))
                    .build();
            conversation.addParticipant(learner);
            conversation.addParticipant(peer);
            conversation = conversationRepository.save(conversation);

            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .sender(peer)
                    .content(demo.opening())
                    .isRead(true)
                    .build());
            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .sender(learner)
                    .content(demo.reply())
                    .isRead(true)
                    .build());
            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .sender(peer)
                    .content(demo.followUp())
                    .isRead(false)
                    .build());
        }
    }

    private void seedPosts(List<Profile> peers, Language language) {
        List<DemoPost> posts = demoPosts(language);
        for (int i = 0; i < peers.size() && i < posts.size(); i++) {
            Profile author = peers.get(i);
            DemoPost demo = posts.get(i);
            List<String> imageUrls = seedImageResolverService.resolveSeedImageUrls(demo.imageSources());
            List<Post> existingPosts = postRepository.findByAuthorIdAndOriginalLanguage(author.getId(),
                    language.getCode());

            Post post = existingPosts.stream()
                    .min(Comparator.comparing(Post::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElseGet(() -> postRepository.save(Post.builder()
                            .author(author)
                            .content(demo.content())
                            .originalLanguage(language.getCode())
                            .imageUrl(imageUrls.get(0))
                            .latitude(demo.latitude())
                            .longitude(demo.longitude())
                            .status(PostStatus.ACTIVE)
                            .build()));

            post.setContent(demo.content());
            post.setOriginalLanguage(language.getCode());
            post.setImageUrl(imageUrls.get(0));
            post.setLatitude(demo.latitude());
            post.setLongitude(demo.longitude());
            post.setStatus(PostStatus.ACTIVE);
            ensurePostImages(post, imageUrls);
            seedPostEngagement(post, peers);
        }
    }

    private void ensurePostImages(Post post, List<String> imageUrls) {
        if (!imageUrls.isEmpty()) {
            post.setImageUrl(imageUrls.get(0));
        }

        java.util.Map<Integer, PostImage> existingByPosition = post.getImages().stream()
                .collect(java.util.stream.Collectors.toMap(
                        PostImage::getPosition,
                        image -> image,
                        (first, ignored) -> first));
        for (int i = 0; i < imageUrls.size(); i++) {
            PostImage existing = existingByPosition.get(i);
            if (existing != null) {
                existing.setImageUrl(imageUrls.get(i));
                continue;
            }
            post.getImages().add(PostImage.builder()
                    .post(post)
                    .imageUrl(imageUrls.get(i))
                    .position(i)
                    .build());
        }
        postRepository.save(post);
    }

    private void seedPostEngagement(Post post, List<Profile> peers) {
        List<Profile> others = peers.stream()
                .filter(peer -> !peer.getId().equals(post.getAuthor().getId()))
                .toList();

        ReactionType[] reactions = { ReactionType.LIKE, ReactionType.HELPFUL, ReactionType.LOVE };
        for (int i = 0; i < others.size(); i++) {
            Profile reactor = others.get(i);
            if (postReactionRepository.findByPostIdAndProfileId(post.getId(), reactor.getId()).isPresent()) {
                continue;
            }
            postReactionRepository.save(PostReaction.builder()
                    .id(new PostReaction.PostReactionId(post.getId(), reactor.getId()))
                    .post(post)
                    .profile(reactor)
                    .type(reactions[i % reactions.length])
                    .build());
        }

        if (postCommentRepository.countByPostId(post.getId()) > 0) {
            return;
        }
        for (int i = 0; i < Math.min(2, others.size()); i++) {
            postCommentRepository.save(PostComment.builder()
                    .post(post)
                    .author(others.get(i))
                    .content(i == 0
                            ? "This is a handy phrase set for practice."
                            : "Saving this for my next conversation session.")
                    .build());
        }
    }

    private void seedMeetups(List<Profile> peers, Language language) {
        List<DemoMeetup> meetups = demoMeetups(language);
        for (int i = 0; i < peers.size() && i < meetups.size(); i++) {
            Profile organizer = peers.get(i);
            DemoMeetup demo = meetups.get(i);
            List<Meetup> existingMeetups = meetupRepository.findByOrganizerIdAndLanguageCode(
                    organizer.getId(), language.getCode());

            Meetup meetup = existingMeetups.stream()
                    .min(Comparator.comparing(Meetup::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElseGet(() -> meetupRepository.save(Meetup.builder()
                            .organizer(organizer)
                            .title(demo.title())
                            .description(demo.description())
                            .language(language)
                            .meetupDate(LocalDateTime.now().plusDays(demo.daysFromNow()))
                            .location(demo.location())
                            .latitude(demo.latitude())
                            .longitude(demo.longitude())
                            .maxAttendees(8)
                            .status(MeetupStatus.UPCOMING)
                            .build()));

            meetup.setTitle(demo.title());
            meetup.setDescription(demo.description());
            meetup.setLanguage(language);
            meetup.setMeetupDate(LocalDateTime.now().plusDays(demo.daysFromNow()));
            meetup.setLocation(demo.location());
            meetup.setLatitude(demo.latitude());
            meetup.setLongitude(demo.longitude());
            meetup.setMaxAttendees(8);
            meetup.setStatus(MeetupStatus.UPCOMING);
            meetup = meetupRepository.save(meetup);
            seedMeetupAttendees(meetup, peers, i);
        }
    }

    private void seedMeetupAttendees(Meetup meetup, List<Profile> peers, int organizerIndex) {
        List<Profile> attendees = List.of(
                meetup.getOrganizer(),
                peers.get((organizerIndex + 1) % peers.size()),
                peers.get((organizerIndex + 2) % peers.size()));

        attendees.stream()
                .distinct()
                .filter(attendee -> !meetupAttendeeRepository.existsByMeetupIdAndAttendeeId(
                        meetup.getId(), attendee.getId()))
                .forEach(attendee -> meetupAttendeeRepository.save(MeetupAttendee.builder()
                        .meetup(meetup)
                        .attendee(attendee)
                        .joinedAt(Instant.now())
                        .build()));
    }

    private void seedWords(Profile learner, Language language) {
        retireJapaneseRomajiWordsForDemoProfile(learner, language);
        demoWords(language.getCode()).stream()
                .filter(word -> !savedWordRepository.existsByUserIdAndWordAndLanguageCode(
                        learner.getId(), word.word(), language.getCode()))
                .forEach(word -> savedWordRepository.save(SavedWord.builder()
                        .user(learner)
                        .word(word.word())
                        .translation(word.translation())
                        .languageCode(language.getCode())
                        .source(SourceType.MANUAL)
                        .context(word.context())
                        .masteryLevel(word.masteryLevel())
                        .nextReview(Instant.now().plusSeconds(word.reviewOffsetSeconds()))
                        .build()));
    }

    private void retireJapaneseRomajiWordsForDemoProfile(Profile learner, Language language) {
        if (!"ja".equals(language.getCode()) || !isDemoManagedProfile(learner)) {
            return;
        }

        List<SavedWord> retiredWords = savedWordRepository.findByUserIdAndLanguageCode(
                        learner.getId(), "ja", org.springframework.data.domain.Pageable.unpaged())
                .getContent()
                .stream()
                .filter(savedWord -> RETIRED_JAPANESE_ROMAJI_WORDS.contains(normalize(savedWord.getWord())))
                .toList();
        if (!retiredWords.isEmpty()) {
            savedWordRepository.deleteAll(retiredWords);
        }
    }

    private boolean isDemoManagedProfile(Profile profile) {
        String email = normalize(profile.getEmail());
        return "demo.japanese@locale.app".equals(email)
                || "demo@locale.app".equals(email)
                || email.endsWith("@locale.demo");
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    private String languageLabel(Language language) {
        return language.getName() == null || language.getName().isBlank()
                ? language.getCode().toUpperCase(Locale.ROOT)
                : language.getName();
    }

    private List<DemoPeer> demoPeers(Language language) {
        String label = languageLabel(language);
        if ("es".equals(language.getCode())) {
            return List.of(
                    new DemoPeer("sofia", "Sofia Martinez",
                            "Building confidence with everyday Spanish speaking practice.",
                            AVATAR_URLS.get(0), -33.8688, 151.2093),
                    new DemoPeer("mateo", "Mateo Rivera",
                            "Organises low-pressure Spanish chat walks around the city.",
                            AVATAR_URLS.get(1), -33.8731, 151.2065),
                    new DemoPeer("lucia", "Lucia Chen",
                            "Learning Spanish for travel, recipes, and music.",
                            AVATAR_URLS.get(2), -33.8650, 151.2140));
        }
        if ("ja".equals(language.getCode())) {
            return List.of(
                    new DemoPeer("aiko", "田中あいこ",
                            "東京旅行の前に、カフェで使う日本語を練習しています。",
                            AVATAR_URLS.get(0), -33.8688, 151.2093),
                    new DemoPeer("ren", "佐藤れん",
                            "毎日使える短い日本語表現と文法メモを共有しています。",
                            AVATAR_URLS.get(1), -33.8731, 151.2065),
                    new DemoPeer("maya", "ブルックス真矢",
                            "市場の食べ物や電車の表現で日本語を学んでいます。",
                            AVATAR_URLS.get(2), -33.8650, 151.2140));
        }
        return List.of(
                new DemoPeer("mentor", label + " Mentor",
                        "Friendly " + label + " speaker who likes short daily practice prompts.",
                        AVATAR_URLS.get(0), -33.8688, 151.2093),
                new DemoPeer("guide", label + " Guide",
                        "Organises relaxed " + label + " chats around cafes and markets.",
                        AVATAR_URLS.get(1), -33.8731, 151.2065),
                new DemoPeer("buddy", label + " Buddy",
                        "Shares useful " + label + " phrases from everyday Sydney routines.",
                        AVATAR_URLS.get(2), -33.8650, 151.2140));
    }

    private List<DemoMessage> demoMessages(Language language) {
        String label = languageLabel(language);
        if ("ja".equals(language.getCode())) {
            return List.of(
                    new DemoMessage("今週、自己紹介の日本語を練習しませんか？",
                            "はい、もっと自然に話せるようになりたいです。",
                            "いいですね。集まりの前に使える表現を三つ送りました。"),
                    new DemoMessage("カフェのメニューで役に立つ食べ物の言葉を見つけました。",
                            "ありがとうございます。練習の前にいくつか保存します。",
                            "まずは最初の五つから始めましょう。よく出てきます。"),
                    new DemoMessage("十分だけ日本語の表現を交換する時間はありますか？",
                            "明日なら大丈夫です。",
                            "よかったです。買い物で使う短い表現リストを持っていきます。"));
        }
        return List.of(
                new DemoMessage("Want to practise beginner " + label + " introductions this week?",
                        "Yes, I would like help sounding more natural.",
                        "Great. I sent three phrases you can try before the meetup."),
                new DemoMessage("I found a cafe menu with useful " + label + " food words.",
                        "Perfect, I will save a few before practice.",
                        "Start with the first five. They show up everywhere."),
                new DemoMessage("Are you free for a ten-minute " + label + " phrase exchange?",
                        "Tomorrow works for me.",
                        "Nice. I will bring a tiny shopping phrase list."));
    }

    private List<DemoPost> demoPosts(Language language) {
        String label = languageLabel(language);
        if ("ja".equals(language.getCode())) {
            return List.of(
                    new DemoPost("カフェで「珈琲をお願いします」と言えました。短い表現でも自然に言えるとうれしいです。",
                            List.of(POST_IMAGES.get(0), POST_IMAGES.get(1), POST_IMAGES.get(2)),
                            -33.8705, 151.2089),
                    new DemoPost("市場で野菜の名前を練習しました。よく使う言葉は声に出すと覚えやすいです。",
                            List.of(POST_IMAGES.get(1)),
                            -33.8734, 151.2067),
                    new DemoPost("駅の近くで道案内の練習をしました。「駅はどこですか？」が自然に言えました。",
                            List.of(POST_IMAGES.get(3)),
                            -33.8661, 151.2131));
        }
        return List.of(
                new DemoPost("Cafe practice: three " + label + " ordering phrases finally felt natural today.",
                        List.of(POST_IMAGES.get(0), POST_IMAGES.get(1), POST_IMAGES.get(2)),
                        -33.8705, 151.2089),
                new DemoPost("Market walk with " + label + " food words. I kept repeating the useful ones out loud.",
                        List.of(POST_IMAGES.get(1)),
                        -33.8734, 151.2067),
                new DemoPost("Direction practice around Circular Quay with short " + label + " questions.",
                        List.of(POST_IMAGES.get(3)),
                        -33.8661, 151.2131));
    }

    private List<DemoMeetup> demoMeetups(Language language) {
        String label = languageLabel(language);
        if ("ja".equals(language.getCode())) {
            return List.of(
                    new DemoMeetup("市場で使う日本語",
                            "食べ物を買う表現と短い質問を練習します。",
                            "Carriageworks Farmers Market", 3, -33.8938, 151.1937),
                    new DemoMeetup("初心者向け日本語さんぽ",
                            "あいさつと道案内を、港の近くでゆっくり練習します。",
                            "Circular Quay", 6, -33.8610, 151.2128),
                    new DemoMeetup("カフェで日本語ロールプレイ",
                            "飲み物を注文して、値段を聞き、丁寧な言い方を練習します。",
                            "Town Hall cafe", 9, -33.8730, 151.2060));
        }
        return List.of(
                new DemoMeetup(label + " Market Phrases",
                        "Practise buying food and asking friendly follow-up questions.",
                        "Carriageworks Farmers Market", 3, -33.8938, 151.1937),
                new DemoMeetup("Beginner " + label + " Walk",
                        "Low-pressure introductions and directions around the harbour.",
                        "Circular Quay", 6, -33.8610, 151.2128),
                new DemoMeetup(label + " Cafe Roleplay",
                        "Order drinks, ask prices, and practise polite endings.",
                        "Town Hall cafe", 9, -33.8730, 151.2060));
    }

    private List<DemoWord> demoWords(String languageCode) {
        return switch (languageCode) {
            case "en" -> List.of(
                    word("hello", "a greeting", "Start a casual conversation.", 65, 3600),
                    word("thanks", "gratitude", "Use after someone helps you.", 80, 7200),
                    word("market", "place to buy food", "Useful on a grocery walk.", 45, 10800),
                    word("left", "direction", "Practise giving directions.", 30, 14400),
                    word("right", "direction", "Practise giving directions.", 55, 18000),
                    word("please", "polite request", "Use while ordering.", 25, 21600),
                    word("station", "transport place", "Ask for directions.", 35, 25200),
                    word("water", "drink", "Read labels or order a drink.", 58, 28800),
                    word("coffee", "drink", "Cafe practice.", 72, 32400),
                    word("tomorrow", "next day", "Planning a meetup.", 40, 36000));
            case "fr" -> List.of(
                    word("bonjour", "hello", "Start a daytime conversation.", 65, 3600),
                    word("merci", "thank you", "Use after receiving help.", 80, 7200),
                    word("marche", "market", "Food shopping practice.", 45, 10800),
                    word("gauche", "left", "Directions practice.", 30, 14400),
                    word("droite", "right", "Directions practice.", 55, 18000),
                    word("s il vous plait", "please", "Polite requests.", 25, 21600),
                    word("gare", "station", "Transport signs.", 35, 25200),
                    word("eau", "water", "Ordering drinks.", 58, 28800),
                    word("cafe", "coffee", "Cafe practice.", 72, 32400),
                    word("demain", "tomorrow", "Planning practice.", 40, 36000));
            case "ja" -> List.of(
                    word("こんにちは", "hello", "A friendly daytime greeting.", 65, 3600),
                    word("ありがとう", "thank you", "Useful after ordering.", 80, 7200),
                    word("市場", "market", "Food shopping practice.", 45, 10800),
                    word("左", "left", "Directions practice.", 30, 14400),
                    word("右", "right", "Directions practice.", 55, 18000),
                    word("お願いします", "please", "Polite requests.", 25, 21600),
                    word("駅", "station", "Transport signs.", 35, 25200),
                    word("水", "water", "Ordering drinks.", 58, 28800),
                    word("珈琲", "coffee", "Cafe practice.", 72, 32400),
                    word("明日", "tomorrow", "Planning practice.", 40, 36000));
            case "ko" -> List.of(
                    word("annyeong", "hello", "A casual greeting.", 65, 3600),
                    word("gamsa", "thanks", "Use after receiving help.", 80, 7200),
                    word("sijang", "market", "Food shopping practice.", 45, 10800),
                    word("oenjjok", "left", "Directions practice.", 30, 14400),
                    word("oreunjjok", "right", "Directions practice.", 55, 18000),
                    word("juseyo", "please give me", "Polite requests.", 25, 21600),
                    word("yeok", "station", "Transport signs.", 35, 25200),
                    word("mul", "water", "Ordering drinks.", 58, 28800),
                    word("keopi", "coffee", "Cafe practice.", 72, 32400),
                    word("naeil", "tomorrow", "Planning practice.", 40, 36000));
            case "pt" -> List.of(
                    word("ola", "hello", "Start a casual conversation.", 65, 3600),
                    word("obrigado", "thank you", "Use after receiving help.", 80, 7200),
                    word("mercado", "market", "Food shopping practice.", 45, 10800),
                    word("esquerda", "left", "Directions practice.", 30, 14400),
                    word("direita", "right", "Directions practice.", 55, 18000),
                    word("por favor", "please", "Polite requests.", 25, 21600),
                    word("estacao", "station", "Transport signs.", 35, 25200),
                    word("agua", "water", "Ordering drinks.", 58, 28800),
                    word("cafe", "coffee", "Cafe practice.", 72, 32400),
                    word("amanha", "tomorrow", "Planning practice.", 40, 36000));
            case "vi" -> List.of(
                    word("xin chao", "hello", "Start a casual conversation.", 65, 3600),
                    word("cam on", "thank you", "Use after receiving help.", 80, 7200),
                    word("cho", "market", "Food shopping practice.", 45, 10800),
                    word("trai", "left", "Directions practice.", 30, 14400),
                    word("phai", "right", "Directions practice.", 55, 18000),
                    word("lam on", "please", "Polite requests.", 25, 21600),
                    word("nha ga", "station", "Transport signs.", 35, 25200),
                    word("nuoc", "water", "Ordering drinks.", 58, 28800),
                    word("ca phe", "coffee", "Cafe practice.", 72, 32400),
                    word("ngay mai", "tomorrow", "Planning practice.", 40, 36000));
            default -> List.of(
                    word("hola", "hello", "Start a casual conversation.", 65, 3600),
                    word("gracias", "thank you", "Use after someone helps you.", 80, 7200),
                    word("mercado", "market", "Useful on a grocery walk.", 45, 10800),
                    word("izquierda", "left", "Practise giving directions.", 30, 14400),
                    word("derecha", "right", "Practise giving directions.", 55, 18000),
                    word("por favor", "please", "Ordering politely.", 25, 21600),
                    word("estacion", "station", "Transport signs.", 35, 25200),
                    word("agua", "water", "Ordering drinks.", 58, 28800),
                    word("cafe", "coffee", "Cafe practice.", 72, 32400),
                    word("manana", "tomorrow", "Planning practice.", 40, 36000));
        };
    }

    private DemoWord word(String word, String translation, String context, int masteryLevel,
            long reviewOffsetSeconds) {
        return new DemoWord(word, translation, context, masteryLevel, reviewOffsetSeconds);
    }

    private record DemoPeer(String usernameSlug, String displayName, String bio, String avatarUrl, Double latitude,
            Double longitude) {
    }

    private record DemoMessage(String opening, String reply, String followUp) {
    }

    private record DemoPost(String content, List<SeedImageSource> imageSources, Double latitude, Double longitude) {
    }

    private record DemoMeetup(String title, String description, String location, int daysFromNow, Double latitude,
            Double longitude) {
    }

    private record DemoWord(String word, String translation, String context, int masteryLevel,
            long reviewOffsetSeconds) {
    }
}
