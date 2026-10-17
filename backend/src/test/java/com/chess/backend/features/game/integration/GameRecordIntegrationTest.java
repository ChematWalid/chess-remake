package com.chess.backend.features.game.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chess.backend.features.social.dto.FriendRequestSummary;
import com.chess.backend.features.social.service.FriendService;
import com.chess.backend.features.social.domain.enums.FriendshipStatus;
import com.chess.backend.features.challenge.domain.GameChallenge;
import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import com.chess.backend.features.challenge.repository.GameChallengeRepository;
import com.chess.backend.features.challenge.service.ChallengeService;
import com.chess.backend.features.challenge.dto.SendChallengeRequest;
import com.chess.backend.features.game.repository.GameHistoryRepository;
import com.chess.backend.features.game.dto.GameStartRequest;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.repository.UserRepository;
import com.chess.backend.features.user.service.ProfileService;
import com.chess.backend.features.user.service.UserDirectory;
import com.chess.backend.features.user.storage.FileStorage;
import com.chess.backend.features.user.storage.StoredFile;
import com.chess.backend.features.auth.security.JwtService;
import com.chess.backend.common.domain.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.frontend-url=http://localhost:5173",
        "app.jwt-secret=chess-test-jwt-secret-at-least-32-bytes-long",
        "app.engine-jwt-secret=chess-test-engine-jwt-secret-at-least-32-bytes",
        "app.internal-token=chess-test-internal-webhook-secret-32-bytes",
        "app.s3-endpoint=http://localhost:9000",
        "management.health.mail.enabled=false",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Testcontainers
class GameRecordIntegrationTest {
    private static final String INTERNAL_TOKEN = "chess-test-internal-webhook-secret-32-bytes";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("chess_test")
            .withUsername("chess")
            .withPassword("chess");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private GameChallengeRepository challenges;

    @Autowired
    private GameHistoryRepository histories;

    @Autowired
    private Clock clock;

    @Autowired
    private FriendService friendService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private UserDirectory userDirectory;

    @Autowired
    private ChallengeService challengeService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileStorage fileStorage;

    @MockBean
    private JavaMailSender mailSender;


    @BeforeEach
    void clearDatabase() {
        histories.deleteAll();
        challenges.deleteAll();
        users.deleteAll();
        reset(fileStorage);
        reset(mailSender);
    }

    @Test
    void recordsGameAndTreatsLegacyRoomTokenAsAnIgnoredField() throws Exception {
        GameChallenge challenge = acceptedChallenge("ROOM01");

        mockMvc.perform(post("/api/internal/games/record")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(challenge, "ROOM01", "ROOM01", false, "1")))
                .andExpect(status().isCreated());

        assertThat(histories.findByRoomCode("ROOM01")).isPresent();
        assertThat(users.findById(challenge.getChallenger().getId()).orElseThrow().getWins()).isEqualTo(1);
        assertThat(users.findById(challenge.getChallenged().getId()).orElseThrow().getLosses()).isEqualTo(1);
        assertThat(challenges.findById(challenge.getId()).orElseThrow().getStatus()).isEqualTo(ChallengeStatus.COMPLETED);
    }

    @Test
    void rejectsWebhookWithoutInternalCredential() throws Exception {
        GameChallenge challenge = acceptedChallenge("ROOM02");

        mockMvc.perform(post("/api/internal/games/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(challenge, "ROOM02", "not-a-room-token", false, "1")))
                .andExpect(status().isUnauthorized());

        assertThat(histories.count()).isZero();
    }

    @Test
    void engineMustConfirmBothPlayersBeforeARecordCanBeSaved() throws Exception {
        GameChallenge challenge = acceptedChallenge("ROOM07");
        challenge.setStatus(ChallengeStatus.ACCEPTED);
        challenge.markStarted(null);
        GameChallenge unstated = challenges.save(challenge);

        mockMvc.perform(post("/api/internal/games/record")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(unstated, "ROOM07", "ROOM07", false, "1")))
                .andExpect(status().isConflict());

        assertThat(histories.findByRoomCode("ROOM07")).isEmpty();
    }

    @Test
    void engineStartHandshakeIsAuthenticatedAndIdempotent() throws Exception {
        GameChallenge challenge = acceptedChallenge("ROOM08");
        challenge.markStarted(null);
        challenges.save(challenge);
        String request = objectMapper.writeValueAsString(new GameStartRequest(
                "ROOM08", challenge.getChallenger().getId().toString(),
                challenge.getChallenged().getId().toString()));

        mockMvc.perform(post("/api/internal/games/start").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/internal/games/start").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/internal/games/start").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isNoContent());

        assertThat(challenges.findById(challenge.getId()).orElseThrow().getStartedAt()).isNotNull();
    }

    @Test
    void acceptedRoomExpiresWhenNeitherPlayerStartsIt() {
        GameChallenge challenge = acceptedChallenge("ROOM09");
        challenge.accept("ROOM09", GameSide.WHITE, clock.instant().minus(Duration.ofMinutes(3)));
        challenge.markStarted(null);
        challenges.save(challenge);

        challengeService.expireNotStarted();

        assertThat(challenges.findById(challenge.getId()).orElseThrow().getStatus())
                .isEqualTo(ChallengeStatus.EXPIRED);
    }

    @Test
    void rejectsPlayerIdsThatDoNotMatchTheAcceptedRoom() throws Exception {
        GameChallenge challenge = acceptedChallenge("ROOM05");
        String payload = payload(challenge, "ROOM05", "ROOM05", false, "1")
                .replace("\"whitePlayerId\": \"" + challenge.getChallenger().getId() + "\"",
                        "\"whitePlayerId\": \"999999\"");

        mockMvc.perform(post("/api/internal/games/record").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());

        assertThat(histories.count()).isZero();
        assertThat(users.findById(challenge.getChallenger().getId()).orElseThrow().getTotalGames()).isZero();
    }

    @Test
    void rejectsInconsistentReplayArrays() throws Exception {
        GameChallenge challenge = acceptedChallenge("ROOM06");
        String payload = payload(challenge, "ROOM06", "ROOM06", false, "1")
                .replace("\"fens\": [\"start\", \"after-e4\"]", "\"fens\": []");

        mockMvc.perform(post("/api/internal/games/record").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        assertThat(histories.count()).isZero();
    }

    @Test
    void repeatedWebhookDeliveryDoesNotDuplicateHistoryOrStats() throws Exception {
        GameChallenge challenge = acceptedChallenge("ROOM03");
        String payload = payload(challenge, "ROOM03", "ROOM03", true, null);

        mockMvc.perform(post("/api/internal/games/record").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/internal/games/record").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        assertThat(histories.count()).isEqualTo(1);
        assertThat(users.findById(challenge.getChallenger().getId()).orElseThrow().getDraws()).isEqualTo(1);
        assertThat(users.findById(challenge.getChallenged().getId()).orElseThrow().getDraws()).isEqualTo(1);
    }

    @Test
    void concurrentWebhookDeliveryIsIdempotent() throws Exception {
        GameChallenge challenge = acceptedChallenge("ROOM04");
        String payload = payload(challenge, "ROOM04", "ROOM04", false, "1");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> submitTogether(payload, ready, start));
            Future<Integer> second = executor.submit(() -> submitTogether(payload, ready, start));
            ready.await();
            start.countDown();

            assertThat(first.get()).isIn(200, 201);
            assertThat(second.get()).isIn(200, 201);
        }

        assertThat(histories.count()).isEqualTo(1);
        assertThat(users.findById(challenge.getChallenger().getId()).orElseThrow().getTotalGames()).isEqualTo(1);
        assertThat(users.findById(challenge.getChallenged().getId()).orElseThrow().getTotalGames()).isEqualTo(1);
    }

    @Test
    void servesOpenApiWithResponseEnvelopeAndSecurityContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users/search'].get.responses['200'].content['application/json'].schema.properties.data").exists())
                .andExpect(jsonPath("$.paths['/api/internal/games/record'].post.security[0].internalToken").exists())
                .andExpect(jsonPath("$.paths['/api/internal/games/record'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/users/me/avatar'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void completesRegistrationEmailVerificationLoginAndRefresh() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new_player","email":"new-player@example.test","password":"correct-horse-10","displayName":"New Player"}
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        org.mockito.Mockito.verify(mailSender).send(mail.capture());
        String message = mail.getValue().getText();
        String verificationToken = message.substring(message.lastIndexOf("token=") + "token=".length()).trim();
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("token", verificationToken))))
                .andExpect(status().isOk());

        var login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"new_player","password":"correct-horse-10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String refreshToken = login.getResponse().getCookie("chess_refresh").getValue();

        mockMvc.perform(post("/api/auth/refresh").cookie(new MockCookie("chess_refresh", refreshToken))
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void managesFriendRequestsAndRespectsSearchability() {
        User viewer = verifiedUser("viewer-user");
        User target = verifiedUser("target-user");
        User hidden = verifiedUser("hidden-user");
        hidden.setSearchable(false);

        friendService.request(viewer.getId(), target.getUsername());
        List<FriendRequestSummary> incoming = friendService.incoming(target.getId());
        assertThat(incoming).hasSize(1);
        friendService.accept(target.getId(), incoming.getFirst().requestId());
        assertThat(friendService.friends(viewer.getId()).stream().map(friend -> friend.username()).toList())
                .containsExactly(target.getUsername());

        var search = profileService.search(viewer.getId(), "target", 0, 10);
        assertThat(search.items().stream().map(result -> result.username()).toList())
                .containsExactly(target.getUsername())
                .doesNotContain(hidden.getUsername());
        assertThat(search.items().getFirst().publicId()).isEqualTo(target.getPublicId());
    }

    @Test
    void usesOpaqueIdsForProfilesAvatarsBlocksAndOtherPlayerHistory() throws Exception {
        User viewer = verifiedUser("public-id-viewer");
        GameChallenge game = acceptedChallenge("PUBID1");
        User publicProfile = game.getChallenger();
        publicProfile.setAvatarKey("avatar-key");
        users.save(publicProfile);
        when(fileStorage.retrieve("avatar-key")).thenReturn(new StoredFile(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), "image/png", 3));
        String token = jwtService.createAccessToken(viewer.getId(), viewer.getUsername(), UserRole.ROLE_USER);

        mockMvc.perform(get("/api/users/{username}/profile", game.getChallenger().getUsername())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId").value(game.getChallenger().getPublicId().toString()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.avatarUrl").value("/api/users/" + game.getChallenger().getPublicId() + "/avatar"));

        mockMvc.perform(get("/api/users/{publicId}/avatar", game.getChallenger().getPublicId()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"));

        mockMvc.perform(get("/api/users/search").param("q", game.getChallenger().getUsername())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].publicId").value(game.getChallenger().getPublicId().toString()))
                .andExpect(jsonPath("$.data.items[0].id").doesNotExist());

        mockMvc.perform(post("/api/friends/block/{publicUserId}", game.getChallenger().getPublicId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        String body = payload(game, "PUBID1", "PUBID1", false, "1");
        mockMvc.perform(post("/api/internal/games/record").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/games/user/{publicUserId}", game.getChallenger().getPublicId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].whitePublicUserId")
                        .value(game.getChallenger().getPublicId().toString()))
                .andExpect(jsonPath("$.data.items[0].blackPublicUserId")
                        .value(game.getChallenged().getPublicId().toString()))
                .andExpect(jsonPath("$.data.items[0].winnerPublicUserId")
                        .value(game.getChallenger().getPublicId().toString()));
    }

    @Test
    void recoversChallengeAfterReconnectAndReturnsPlayerSpecificConnectionToken() throws Exception {
        User challenger = verifiedUser("challenge-out-user");
        User challenged = verifiedUser("challenge-in-user");
        friendService.request(challenger.getId(), challenged.getUsername());
        friendService.accept(challenged.getId(), friendService.incoming(challenged.getId()).getFirst().requestId());
        String challengerToken = jwtService.createAccessToken(challenger.getId(), challenger.getUsername(), UserRole.ROLE_USER);
        String challengedToken = jwtService.createAccessToken(challenged.getId(), challenged.getUsername(), UserRole.ROLE_USER);

        challengeService.send(challenger.getId(), new SendChallengeRequest(challenged.getUsername(), GameSide.WHITE, 300, 0));

        String outgoing = mockMvc.perform(get("/api/challenges/outgoing")
                        .header("Authorization", "Bearer " + challengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long challengeId = objectMapper.readTree(outgoing).path("data").path("items").get(0).path("challengeId").asLong();
        assertThat(challengeId).isPositive();
        challengeService.accept(challenged.getId(), challengeId);

        var white = mockMvc.perform(get("/api/challenges/{challengeId}", challengeId)
                        .header("Authorization", "Bearer " + challengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.playerColor").value("WHITE"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        var black = mockMvc.perform(get("/api/challenges/{challengeId}", challengeId)
                        .header("Authorization", "Bearer " + challengedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.playerColor").value("BLACK"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(white).contains(challenged.getPublicId().toString());
        assertThat(black).contains(challenger.getPublicId().toString());
    }

    @Test
    void retryAfterDeclineUsesTheRetryingUserAsRequester() {
        User first = verifiedUser("friend-first-user");
        User second = verifiedUser("friend-second-user");

        friendService.request(first.getId(), second.getUsername());
        long requestId = friendService.incoming(second.getId()).getFirst().requestId();
        friendService.decline(second.getId(), requestId);
        friendService.request(second.getId(), first.getUsername());

        var incoming = friendService.incoming(first.getId()).getFirst();
        var outgoing = friendService.outgoing(second.getId()).getFirst();
        assertThat(incoming.publicUserId()).isEqualTo(second.getPublicId());
        assertThat(outgoing.publicUserId()).isEqualTo(first.getPublicId());
        assertThat(outgoing.status()).isEqualTo(FriendshipStatus.PENDING);
    }

    private int submitTogether(String payload, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/internal/games/record").header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse().getStatus();
    }

    private GameChallenge acceptedChallenge(String roomCode) {
        User challenger = verifiedUser("white-" + roomCode.toLowerCase());
        User challenged = verifiedUser("black-" + roomCode.toLowerCase());
        GameChallenge challenge = new GameChallenge(challenger, challenged, 300, 0, GameSide.WHITE,
                clock.instant().plus(Duration.ofMinutes(5)));
        challenge.accept(roomCode, GameSide.WHITE, clock.instant());
        challenge.markStarted(clock.instant());
        return challenges.save(challenge);
    }

    private User verifiedUser(String username) {
        User user = new User(username, username + "@example.test", null, username);
        user.setEmailVerified(true);
        return users.save(user);
    }

    @Test
    void userCanReadAndUpdateCrossDeviceSettings() throws Exception {
        User user = userDirectory.create(new User("settings-player", "settings-player@example.test", null, "Settings Player"));
        String token = jwtService.createAccessToken(user.getId(), user.getUsername(), UserRole.ROLE_USER);

        mockMvc.perform(get("/api/users/me/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boardPalette").value("FOREST"))
                .andExpect(jsonPath("$.data.themePreference").value("DARK"))
                .andExpect(jsonPath("$.data.showCoordinates").value(true));

        mockMvc.perform(put("/api/users/me/settings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardPalette":"WALNUT","boardOrientation":"BLACK","motionPreference":"REDUCED",
                                "themePreference":"LIGHT","showCoordinates":false,"showLegalMoveHints":false,
                                "premovesEnabled":false,"moveConfirmation":true,"gameSoundsEnabled":true,
                                "inAppNotificationsEnabled":false,"notificationSoundsEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boardPalette").value("WALNUT"))
                .andExpect(jsonPath("$.data.themePreference").value("LIGHT"))
                .andExpect(jsonPath("$.data.notificationSoundsEnabled").value(true));
    }

    private String payload(GameChallenge challenge, String roomCode, String legacyRoomToken, boolean isDraw,
                           String winnerId) {
        String winnerJson = winnerId == null ? "null" : "\"" + (winnerId.equals("1")
                ? challenge.getChallenger().getId() : challenge.getChallenged().getId()) + "\"";
        return """
                {
                  "roomCode": "%s",
                  "roomToken": "%s",
                  "whitePlayerId": "%s",
                  "blackPlayerId": "%s",
                  "winnerPlayerId": %s,
                  "isDraw": %s,
                  "terminationReason": "CHECKMATE",
                  "reason_code": "CHECKMATE",
                  "totalMoves": 1,
                  "durationSeconds": 30,
                  "pgn": "1. e4 1-0",
                  "moves": ["e4"],
                  "fens": ["start", "after-e4"]
                }
                """.formatted(roomCode, legacyRoomToken, challenge.getChallenger().getId(),
                challenge.getChallenged().getId(), winnerJson, isDraw);
    }
}
