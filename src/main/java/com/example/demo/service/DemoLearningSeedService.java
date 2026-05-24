package com.example.demo.service;

import com.example.demo.entity.Conversation;
import com.example.demo.entity.Friend;
import com.example.demo.entity.Language;
import com.example.demo.entity.Meetup;
import com.example.demo.entity.Message;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostImage;
import com.example.demo.entity.Profile;
import com.example.demo.entity.SavedWord;
import com.example.demo.entity.UserLanguage;
import com.example.demo.enums.FriendStatus;
import com.example.demo.enums.LocationVisibility;
import com.example.demo.enums.MeetupStatus;
import com.example.demo.enums.PostStatus;
import com.example.demo.enums.ProficiencyLevel;
import com.example.demo.enums.SourceType;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.FriendRepository;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.MeetupRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.SavedWordRepository;
import com.example.demo.repository.UserLanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DemoLearningSeedService {

    private final ProfileRepository profileRepository;
    private final LanguageRepository languageRepository;
    private final UserLanguageRepository userLanguageRepository;
    private final SavedWordRepository savedWordRepository;
    private final FriendRepository friendRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PostRepository postRepository;
    private final MeetupRepository meetupRepository;

    @Transactional
    public void seedForLearningLanguages(Profile learner, List<String> languageCodes) {
        languageCodes.stream()
                .map(this::normalize)
                .filter(code -> code.equals("es") || code.equals("ja"))
                .distinct()
                .forEach(code -> seedLanguage(learner, code));
    }

    private void seedLanguage(Profile learner, String languageCode) {
        if (savedWordRepository.countByUserIdAndLanguageCode(learner.getId(), languageCode) > 0) {
            return;
        }

        Language language = languageRepository.findByCode(languageCode)
                .orElseThrow(() -> new IllegalStateException("Language not found: " + languageCode));
        List<DemoPeer> peers = demoPeers(languageCode);
        List<Profile> peerProfiles = peers.stream()
                .map(peer -> createPeer(learner.getId(), language, peer))
                .toList();

        seedFriendships(learner, peerProfiles);
        seedConversations(learner, peerProfiles, languageCode);
        seedPosts(peerProfiles, languageCode);
        seedMeetups(peerProfiles, language);
        seedWords(learner, languageCode);
    }

    private Profile createPeer(UUID learnerId, Language language, DemoPeer peer) {
        String suffix = learnerId.toString().substring(0, 8);
        String username = "demo_" + language.getCode() + "_" + peer.usernameSlug() + "_" + suffix;
        String email = username + "@locale.demo";

        return profileRepository.findByEmail(email)
                .orElseGet(() -> {
                    Profile profile = Profile.builder()
                            .username(username)
                            .email(email)
                            .passwordHash("demo-seeded-profile")
                            .displayName(peer.displayName())
                            .avatarUrl(peer.avatarUrl())
                            .bio(peer.bio())
                            .latitude(peer.latitude())
                            .longitude(peer.longitude())
                            .location("Sydney NSW")
                            .locationVisibility(LocationVisibility.PUBLIC)
                            .build();
                    profile = profileRepository.save(profile);

                    userLanguageRepository.save(UserLanguage.builder()
                            .profile(profile)
                            .language(language)
                            .proficiency(ProficiencyLevel.BEGINNER)
                            .isLearning(true)
                            .build());

                    return profile;
                });
    }

    private void seedFriendships(Profile learner, List<Profile> peers) {
        peers.stream()
                .filter(peer -> !friendRepository.areFriends(learner.getId(), peer.getId()))
                .forEach(peer -> friendRepository.save(Friend.builder()
                        .requester(peer)
                        .receiver(learner)
                        .status(FriendStatus.ACCEPTED)
                        .build()));
    }

    private void seedConversations(Profile learner, List<Profile> peers, String languageCode) {
        List<DemoMessage> messages = demoMessages(languageCode);
        for (int i = 0; i < peers.size() && i < messages.size(); i++) {
            Profile peer = peers.get(i);
            DemoMessage demo = messages.get(i);
            if (conversationRepository.findBetweenUsers(learner.getId(), peer.getId()).isPresent()) {
                continue;
            }

            Conversation conversation = Conversation.builder()
                    .isGroup(false)
                    .lastMessagePreview(demo.reply())
                    .lastMessageAt(LocalDateTime.now().minusHours(i + 1L))
                    .build();
            conversation.addParticipant(learner);
            conversation.addParticipant(peer);
            conversation = conversationRepository.save(conversation);

            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .sender(peer)
                    .content(demo.opening())
                    .isRead(false)
                    .build());
            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .sender(learner)
                    .content(demo.reply())
                    .isRead(true)
                    .build());
        }
    }

    private void seedPosts(List<Profile> peers, String languageCode) {
        List<DemoPost> posts = demoPosts(languageCode);
        for (int i = 0; i < peers.size() && i < posts.size(); i++) {
            if (postRepository.countByAuthorIdAndOriginalLanguage(peers.get(i).getId(), languageCode) > 0) {
                continue;
            }
            DemoPost demo = posts.get(i);
            Post post = Post.builder()
                    .author(peers.get(i))
                    .content(demo.content())
                    .originalLanguage(languageCode)
                    .imageUrl(demo.imageUrl())
                    .latitude(demo.latitude())
                    .longitude(demo.longitude())
                    .status(PostStatus.ACTIVE)
                    .build();
            post.getImages().add(PostImage.builder()
                    .post(post)
                    .imageUrl(demo.imageUrl())
                    .position(0)
                    .build());
            postRepository.save(post);
        }
    }

    private void seedMeetups(List<Profile> peers, Language language) {
        List<DemoMeetup> meetups = demoMeetups(language.getCode());
        for (int i = 0; i < peers.size() && i < meetups.size(); i++) {
            if (meetupRepository.countByOrganizerIdAndLanguageCode(peers.get(i).getId(), language.getCode()) > 0) {
                continue;
            }
            DemoMeetup demo = meetups.get(i);
            meetupRepository.save(Meetup.builder()
                    .organizer(peers.get(i))
                    .title(demo.title())
                    .description(demo.description())
                    .language(language)
                    .meetupDate(LocalDateTime.now().plusDays(demo.daysFromNow()))
                    .location(demo.location())
                    .latitude(demo.latitude())
                    .longitude(demo.longitude())
                    .maxAttendees(8)
                    .status(MeetupStatus.UPCOMING)
                    .build());
        }
    }

    private void seedWords(Profile learner, String languageCode) {
        demoWords(languageCode).forEach(word -> savedWordRepository.save(SavedWord.builder()
                .user(learner)
                .word(word.word())
                .translation(word.translation())
                .languageCode(languageCode)
                .source(SourceType.MANUAL)
                .context(word.context())
                .masteryLevel(word.masteryLevel())
                .nextReview(Instant.now().plusSeconds(word.reviewOffsetSeconds()))
                .build()));
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    private List<DemoPeer> demoPeers(String languageCode) {
        if (languageCode.equals("ja")) {
            return List.of(
                    new DemoPeer("aiko", "Aiko Tanaka", "Practising cafe conversations before a Tokyo trip.",
                            "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=240&h=240&fit=crop&crop=faces", -33.8688, 151.2093),
                    new DemoPeer("ren", "Ren Sato", "Shares short daily Japanese prompts and grammar notes.",
                            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=240&h=240&fit=crop&crop=faces", -33.8731, 151.2065),
                    new DemoPeer("maya", "Maya Brooks", "Learning Japanese through food markets and transit phrases.",
                            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=240&h=240&fit=crop&crop=faces", -33.8650, 151.2140));
        }

        return List.of(
                new DemoPeer("sofia", "Sofia Martinez", "Building confidence with everyday Spanish speaking practice.",
                        "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?w=240&h=240&fit=crop&crop=faces", -33.8688, 151.2093),
                new DemoPeer("mateo", "Mateo Rivera", "Organises low-pressure Spanish chat walks around the city.",
                        "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=240&h=240&fit=crop&crop=faces", -33.8731, 151.2065),
                new DemoPeer("lucia", "Lucia Chen", "Learning Spanish for travel, recipes, and music.",
                        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=240&h=240&fit=crop&crop=faces", -33.8650, 151.2140));
    }

    private List<DemoMessage> demoMessages(String languageCode) {
        if (languageCode.equals("ja")) {
            return List.of(
                    new DemoMessage("今週、カフェで「コーヒーをください」を練習しませんか？", "Yes, I want to try the counter phrases."),
                    new DemoMessage("中央駅で「出口」と「改札」の漢字を保存しました。", "Great, send them through before the meetup."),
                    new DemoMessage("今日はひらがな練習と短い会話、どちらがいいですか？", "Short dialogues, please."));
        }

        return List.of(
                new DemoMessage("Want to practise Spanish introductions before the meetup?", "Yes, I need help with natural greetings."),
                new DemoMessage("I posted a market photo with useful Spanish food words.", "Perfect, I will save a few phrases."),
                new DemoMessage("Are you free for a ten-minute voice note exchange?", "Tomorrow works for me."));
    }

    private List<DemoPost> demoPosts(String languageCode) {
        if (languageCode.equals("ja")) {
            return List.of(
                    new DemoPost("静かなラーメン屋を見つけました。メニューの言葉を練習しました：ラーメン、替え玉、お茶。",
                            "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=900&h=600&fit=crop", -33.8705, 151.2089),
                    new DemoPost("今日は駅のホームで道を聞く練習をしました：「駅はどこですか？」",
                            "https://images.unsplash.com/photo-1542051841857-5f90071e7989?w=900&h=600&fit=crop", -33.8734, 151.2067),
                    new DemoPost("スーパーで日本語ラベルを探しました。「水」と「お茶」を覚えました。",
                            "https://images.unsplash.com/photo-1542838132-92c53300491e?w=900&h=600&fit=crop", -33.8661, 151.2131));
        }

        return List.of(
                new DemoPost("Cafe practice today: un cafe, una mesa, and gracias felt natural by the end.",
                        "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=900&h=600&fit=crop", -33.8705, 151.2089),
                new DemoPost("Market walk with Spanish food words: manzana, pan, queso, tomate.",
                        "https://images.unsplash.com/photo-1488459716781-31db52582fe9?w=900&h=600&fit=crop", -33.8734, 151.2067),
                new DemoPost("Practised directions around Circular Quay: izquierda, derecha, todo recto.",
                        "https://images.unsplash.com/photo-1506973035872-a4ec16b8e8d9?w=900&h=600&fit=crop", -33.8661, 151.2131));
    }

    private List<DemoMeetup> demoMeetups(String languageCode) {
        if (languageCode.equals("ja")) {
            return List.of(
                    new DemoMeetup("Japanese Cafe Roleplay", "Order drinks, ask prices, and practise polite endings.", "Town Hall cafe", 3, -33.8730, 151.2060),
                    new DemoMeetup("Hiragana Sign Walk", "Read simple signs and build a shared photo word bank.", "Central Station concourse", 6, -33.8830, 151.2070),
                    new DemoMeetup("Beginner Bento Chat", "Short food and preference dialogues for new learners.", "Darling Square", 9, -33.8775, 151.2020));
        }

        return List.of(
                new DemoMeetup("Spanish Market Phrases", "Practise buying food and asking friendly follow-up questions.", "Carriageworks Farmers Market", 3, -33.8938, 151.1937),
                new DemoMeetup("Beginner Spanish Walk", "Low-pressure introductions and directions around the harbour.", "Circular Quay", 6, -33.8610, 151.2128),
                new DemoMeetup("Tapas Vocabulary Night", "Food words, preferences, and quick table conversations.", "Surry Hills", 9, -33.8847, 151.2090));
    }

    private List<DemoWord> demoWords(String languageCode) {
        if (languageCode.equals("ja")) {
            return List.of(
                    new DemoWord("こんにちは", "hello", "A friendly daytime greeting.", 45, 3600),
                    new DemoWord("ありがとう", "thank you", "Useful after ordering or receiving help.", 70, 7200),
                    new DemoWord("駅", "station", "Ask for directions around transport.", 35, 10800),
                    new DemoWord("水", "water", "Read labels or order a drink.", 55, 14400),
                    new DemoWord("本当に？", "really?", "Useful when reacting to surprising news.", 58, 16200),
                    new DemoWord("お願いします", "please", "Polite ending for requests.", 25, 18000));
        }

        return List.of(
                new DemoWord("hola", "hello", "Start a casual conversation.", 65, 3600),
                new DemoWord("gracias", "thank you", "Use after someone helps you.", 80, 7200),
                new DemoWord("mercado", "market", "Useful on a grocery walk.", 45, 10800),
                new DemoWord("izquierda", "left", "Practise giving directions.", 30, 14400),
                new DemoWord("quiero", "I want", "Ordering food or drinks.", 55, 18000));
    }

    private record DemoPeer(String usernameSlug, String displayName, String bio, String avatarUrl, Double latitude,
            Double longitude) {
    }

    private record DemoMessage(String opening, String reply) {
    }

    private record DemoPost(String content, String imageUrl, Double latitude, Double longitude) {
    }

    private record DemoMeetup(String title, String description, String location, int daysFromNow, Double latitude,
            Double longitude) {
    }

    private record DemoWord(String word, String translation, String context, int masteryLevel,
            long reviewOffsetSeconds) {
    }
}
