package com.example.demo.service;

import com.example.demo.dto.BoundingBoxDTO;
import com.example.demo.dto.CreateCommentRequest;
import com.example.demo.dto.CreateWordRequest;
import com.example.demo.dto.DetectedObjectDTO;
import com.example.demo.dto.PostReactionRequest;
import com.example.demo.entity.Conversation;
import com.example.demo.entity.Notification;
import com.example.demo.entity.NotificationPrefs;
import com.example.demo.entity.Post;
import com.example.demo.entity.Profile;
import com.example.demo.entity.UserSettings;
import com.example.demo.enums.NotificationType;
import com.example.demo.enums.ReactionType;
import com.example.demo.enums.ScannerTranslationSource;
import com.example.demo.enums.SourceType;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.FriendRepository;
import com.example.demo.repository.LanguageRepository;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.PostCommentRepository;
import com.example.demo.repository.PostReactionRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.SavedWordRepository;
import com.example.demo.repository.ScanDetectionRepository;
import com.example.demo.repository.ScanSessionRepository;
import com.example.demo.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationEventIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private ReactionService reactionService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private SavedWordService savedWordService;
    @Autowired
    private ScanSessionService scanSessionService;

    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private FriendRepository friendRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostReactionRepository postReactionRepository;
    @Autowired
    private PostCommentRepository postCommentRepository;
    @Autowired
    private SavedWordRepository savedWordRepository;
    @Autowired
    private ScanDetectionRepository scanDetectionRepository;
    @Autowired
    private ScanSessionRepository scanSessionRepository;
    @Autowired
    private UserSettingsRepository userSettingsRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private LanguageRepository languageRepository;

    private Profile currentUser;
    private Profile otherUser;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        friendRepository.deleteAll();
        postReactionRepository.deleteAll();
        postCommentRepository.deleteAll();
        postRepository.deleteAll();
        savedWordRepository.deleteAll();
        scanDetectionRepository.deleteAll();
        scanSessionRepository.deleteAll();
        userSettingsRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        profileRepository.deleteAll();
        languageRepository.deleteAll();

        currentUser = profileRepository.save(Profile.builder()
                .username("event_user")
                .email("event-user@example.com")
                .displayName("Event User")
                .passwordHash("hash")
                .build());
        otherUser = profileRepository.save(Profile.builder()
                .username("event_other")
                .email("event-other@example.com")
                .displayName("Event Other")
                .passwordHash("hash")
                .build());
    }

    @Test
    void shouldCreateFriendRequestAndAcceptedNotifications() {
        var request = friendService.sendRequest(otherUser.getId(), currentUser.getEmail());

        Notification friendRequest = latestNotificationFor(otherUser);
        assertThat(friendRequest.getType()).isEqualTo(NotificationType.FRIEND_REQUEST);
        assertThat(friendRequest.getActor().getId()).isEqualTo(currentUser.getId());

        friendService.respondToRequest(request.getId(), "accept", otherUser.getEmail());

        Notification accepted = latestNotificationFor(currentUser);
        assertThat(accepted.getType()).isEqualTo(NotificationType.FRIEND_ACCEPTED);
        assertThat(accepted.getActor().getId()).isEqualTo(otherUser.getId());
    }

    @Test
    void shouldCreateMessageNotificationForOtherParticipantsOnly() throws Exception {
        chatService.sendMessage(currentUser.getEmail(), null, otherUser.getId(), "hello", null);

        Notification notification = latestNotificationFor(otherUser);
        assertThat(notification.getType()).isEqualTo(NotificationType.MESSAGE);
        assertThat(notification.getBody()).contains("Event User").contains("hello");
        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(currentUser.getId())).isZero();
    }

    @Test
    void shouldCreatePostReactionAndCommentNotificationsForPostAuthor() {
        Post post = postRepository.save(Post.builder()
                .author(currentUser)
                .content("Post content")
                .originalLanguage("en")
                .build());

        reactionService.reactToPost(post.getId(), PostReactionRequest.builder()
                .reaction(ReactionType.LIKE)
                .build(), otherUser.getEmail());

        Notification reaction = latestNotificationFor(currentUser);
        assertThat(reaction.getType()).isEqualTo(NotificationType.POST_LIKE);

        commentService.addComment(post.getId(), CreateCommentRequest.builder()
                .content("Nice post")
                .build(), otherUser.getEmail());

        Notification comment = latestNotificationFor(currentUser);
        assertThat(comment.getType()).isEqualTo(NotificationType.POST_COMMENT);
    }

    @Test
    void shouldCreateSelfNotificationsForSavedWordAndScan() {
        savedWordService.saveWord(currentUser.getEmail(), CreateWordRequest.builder()
                .word("bonjour")
                .translation("hello")
                .languageCode("fr")
                .source(SourceType.MANUAL)
                .build());

        Notification savedWord = latestNotificationFor(currentUser);
        assertThat(savedWord.getType()).isEqualTo(NotificationType.SAVED_WORD);
        assertThat(savedWord.getActor()).isNull();

        scanSessionService.recordScan(currentUser.getEmail(), List.of(DetectedObjectDTO.builder()
                .label("book")
                .confidence(0.95)
                .nativeWord("book")
                .learningWord("livre")
                .languageCode("fr")
                .translationSource(ScannerTranslationSource.DICTIONARY)
                .box(BoundingBoxDTO.builder()
                        .x(1)
                        .y(2)
                        .width(3)
                        .height(4)
                        .build())
                .build()));

        Notification scan = latestNotificationFor(currentUser);
        assertThat(scan.getType()).isEqualTo(NotificationType.SCAN_DETECTED_WORD);
        assertThat(scan.getActor()).isNull();
    }

    @Test
    void shouldNotCreateSavedWordAndScanNotificationsWhenPushDisabled() {
        userSettingsRepository.save(UserSettings.builder()
                .profile(currentUser)
                .notificationPrefs(NotificationPrefs.builder()
                        .pushEnabled(false)
                        .build())
                .build());

        savedWordService.saveWord(currentUser.getEmail(), CreateWordRequest.builder()
                .word("bonjour")
                .translation("hello")
                .languageCode("fr")
                .source(SourceType.MANUAL)
                .build());

        scanSessionService.recordScan(currentUser.getEmail(), List.of(DetectedObjectDTO.builder()
                .label("book")
                .confidence(0.95)
                .nativeWord("book")
                .learningWord("livre")
                .languageCode("fr")
                .translationSource(ScannerTranslationSource.DICTIONARY)
                .box(BoundingBoxDTO.builder()
                        .x(1)
                        .y(2)
                        .width(3)
                        .height(4)
                        .build())
                .build()));

        assertThat(notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(currentUser.getId(), PageRequest.of(0, 10))
                .getContent())
                .isEmpty();
    }

    private Notification latestNotificationFor(Profile recipient) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(recipient.getId(), PageRequest.of(0, 1))
                .getContent()
                .get(0);
    }
}
