package me.sonam.friendship;

import me.sonam.friendship.persist.entity.Friendship;
import me.sonam.friendship.persist.repo.FriendshipRepository;
import me.sonam.webclients.Mapper;
import me.sonam.webclients.friendship.SeUserFriend;
import me.sonam.webclients.user.User;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * this will test the end-to-end from the Router to business service to entity persistence using in-memory db.
 */
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Application.class, TestConfig.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(MockitoExtension.class)
public class FriendshipRouterIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(FriendshipRouterIntegTest.class);

    private static MockWebServer mockWebServer;
    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    @Value("${friendshipEndpoint}")
    private String friendshipEndpoint;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    ApplicationContext context;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        this.webTestClient = WebTestClient
                .bindToApplicationContext(this.context)
                // add Spring Security test Support
                .apply(springSecurity())
                .configureClient()
                //   .filter(basicAuthentication("user", "password"))
                .build();
    }
    @BeforeAll
    static void setupMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        LOG.info("host: {}, port: {}", mockWebServer.getHostName(), mockWebServer.getPort());
    }

    @AfterAll
    public static void shutdownMockWebServer() throws IOException {
        LOG.info("shutdown and close mockWebServer");
        mockWebServer.shutdown();
        mockWebServer.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws IOException {
        r.add("friendshipEndpoint", () -> "http://localhost:"+ mockWebServer.getPort());
        r.add("userEndpoint", () -> "http://localhost:"+ mockWebServer.getPort());
        r.add("notificationEndpoint", ()->"http://localhost:"+mockWebServer.getPort());
    }

    // Request friendship from user to friend
    @Test
    public void requestFriendship() throws InterruptedException {
        LOG.info("request friendship test");

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        final String authenticationId = "dave";
        Jwt jwt = jwt(authenticationId, userId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        UUID friendId = UUID.randomUUID();
        LOG.info("friendId: {}",friendId);

        requestFriendship(jwt, userId, friendId).subscribe(friendship -> LOG.info("got friendship: {}", friendship));
    }

    private Mono<Friendship> requestFriendship(Jwt jwt, UUID userId, UUID friendId) throws InterruptedException {
        User user = new User(userId, "Kadar Khan");
        final String jsonUser = Mapper.getJson(user);

        //1
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonUser));

        User friend = new User(friendId, "Shambha");
        final String jsonFriend = Mapper.getJson(friend);

        //2
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        //3
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonUser));

        //4
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody("{\"message\": \"friend request notification sent\"}"));

        //5
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonUser));

        String endpoint = "/friendships/request/{userId}";

        // requesting friendship with friendId byuserId
        endpoint = endpoint.replace("{userId}", friendId.toString());
        final String jwtString= "eyJraWQiOiJlOGQ3MjIzMC1iMDgwLTRhZjEtODFkOC0zMzE3NmNhMTM5ODIiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiI3NzI1ZjZmZC1kMzk2LTQwYWYtOTg4Ni1jYTg4YzZlOGZjZDgiLCJhdWQiOiI3NzI1ZjZmZC1kMzk2LTQwYWYtOTg4Ni1jYTg4YzZlOGZjZDgiLCJuYmYiOjE3MTQ3NTY2ODIsImlzcyI6Imh0dHA6Ly9teS1zZXJ2ZXI6OTAwMSIsImV4cCI6MTcxNDc1Njk4MiwiaWF0IjoxNzE0NzU2NjgyLCJqdGkiOiI0NDBlZDY0My00MzdkLTRjOTMtYTZkMi1jNzYxNjFlNDRlZjUifQ.fjqgoczZbbmcnvYpVN4yakpbplp7EkDyxslvar5nXBFa6mgIFcZa29fwIKfcie3oUMQ8MDWxayak5PZ_QIuHwTvKSWHs0WL91ljf-GT1sPi1b4gDKf0rJOwi0ClcoTCRIx9-WGR6t2BBR1Rk6RGF2MW7xKw8M-RMac2A2mPEPJqoh4Pky1KgxhZpEXixegpAdQIvBgc0KBZeQme-ZzTYugB8EPUmGpMlfd-zX_vcR1ijxi8e-LRRJMqmGkc9GXfrH7MOKNQ_nu6pc6Gish2v_iuUEcpPHXrfqzGb9IHCLvfuLSaTDcYKYjQaEUAp-1uDW8-5posjiUV2eBiU48ajYg";

        EntityExchangeResult<SeUserFriend> entityExchangeResult = webTestClient.
                mutateWith(mockJwt().jwt(jwt)).post().uri(endpoint)
                .headers(addJwt(jwt))
                .exchange().expectStatus().isCreated().expectBody(SeUserFriend.class)
                .returnResult();

        //the output must be consumed for the test case to work.
        LOG.info("response: {}", entityExchangeResult.getResponseBody());
        //assertThat(result.getResponseBody()).isEqualTo("delete my account success for user id: "+myUser.getId());

        // 1 userWebClient.findById call is for userId itself
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/"+userId);


        // 2 userWebClient.findById is for friendId
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/"+friendId);

        // 3 userWebClient.findById is for userId
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/"+userId);

        // 4 notification.sendFriendNotification
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/notifications");

        // 5 userWebClient.findById is for userId
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/"+friendId);

        Flux<Friendship> friendshipFlux = friendshipRepository.findByUserIdAndFriendId(userId, friendId);
        friendshipFlux.as(StepVerifier::create).assertNext(friendship -> {
            LOG.info("assert the new friendship created");
            assertThat(friendship.getId()).isNotNull();
            assertThat(friendship.getFriendId()).isEqualTo(friendId);
            assertThat(friendship.getUserId()).isEqualTo(userId);
            assertThat(friendship.getRequestAccepted()).isFalse();
            assertThat(friendship.getResponseSentDate()).isNull();
            assertThat(friendship.getRequestSentDate()).isNotNull();
            assertThat(friendship.isNew()).isFalse();
        }).verifyComplete();

        friendshipFlux = friendshipRepository.findByUserIdAndFriendId(userId, friendId);
        friendshipFlux.as(StepVerifier::create).expectNextCount(1).verifyComplete();
        LOG.info("verified there is only 1 record with userId and friendId");

        return friendshipFlux.single();
    }

    @Test
    public void declineFriendship() throws InterruptedException {
        LOG.info("decline friendship test");

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        final String authenticationId = "dave";
        Jwt jwt = jwt(authenticationId, userId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        UUID friendId = UUID.randomUUID();
        LOG.info("friendId: {}",friendId);

        Friendship friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, false);
        friendshipRepository.save(friendship).subscribe();

        declineFriendship(jwt, friendship.getId()).subscribe(aBoolean -> LOG.info("aBoolean is now {}", aBoolean));
    }

    private Mono<Boolean> declineFriendship(Jwt jwt, UUID friendshipId) {
        LOG.info("decline friendship with id: {}", friendshipId);

        Mono<Boolean> booleanMono =  friendshipRepository.existsById(friendshipId);
        booleanMono.as(StepVerifier::create).assertNext(aBoolean -> {
            assertThat(aBoolean).isTrue();
            LOG.info("assert that boolean is true");

        }).verifyComplete();

        LOG.info("verified there is a friendship with this id");


        String endpoint = "/friendships/decline/{friendshipId}".replace("{friendshipId}", friendshipId.toString());

        EntityExchangeResult<String> entityExchangeResult = webTestClient.
                mutateWith(mockJwt().jwt(jwt)).delete().uri(endpoint)
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk().expectBody(String.class)
                .returnResult();

        //the output must be consumed for the test case to work.
        LOG.info("response: {}", entityExchangeResult.getResponseBody());
        assertThat(entityExchangeResult.getResponseBody()).isEqualTo("{\"message\":\"friendship deleted by id\"}");

        Mono<Boolean> booleanMono1  =  friendshipRepository.existsById(friendshipId);
        booleanMono1.as(StepVerifier::create).assertNext(aBoolean -> {
            assertThat(aBoolean).isFalse();
            LOG.info("verified there is no friendship with id anymore");
        }).verifyComplete();
        return booleanMono;
    }

    @Test
    public void acceptFriendship() throws InterruptedException {
        LOG.info("acceptFriendship endpoint test");

        //for this set friendId to hardcoded value so that we can pass this friendId into jwt
        //which will accept the friendship, user cannot accept only the friend in the friendship can
        UUID friendId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        final String authenticationId = "dave";
        Jwt jwt = jwt(authenticationId, friendId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        UUID userId = UUID.randomUUID();
        LOG.info("friendId: {}",friendId);

        Friendship friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, false);
        friendshipRepository.save(friendship).subscribe();

        acceptFriendship(jwt, userId, friendId, friendship.getId()).subscribe(aBoolean -> LOG.info("aBoolean is now {}", aBoolean));
    }

    private Mono<Boolean> acceptFriendship(Jwt jwt, UUID userId, UUID friendId, UUID friendshipId) throws InterruptedException {
        LOG.info("accept friendship with friend for friendshipId: {}", friendshipId);

        Mono<Boolean> booleanMono =  friendshipRepository.existsById(friendshipId);
        booleanMono.as(StepVerifier::create).assertNext(aBoolean -> {
            assertThat(aBoolean).isTrue();
            LOG.info("assert that boolean is true");

        }).verifyComplete();

        LOG.info("verified there is a friendship with this id");

        final String friendFullName = "Shambha";
        User friend = new User(friendId, friendFullName);
        final String jsonFriend = Mapper.getJson(friend);
        //1
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        User user = new User(userId, "Kadar Khan");
        final String jsonUser = Mapper.getJson(user);

        //2
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonUser));
        //3
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody("{\"message\": \"friend request confirmation acceptance sent\"}"));


        String endpoint = "/friendships/accept/{friendshipId}".replace("{friendshipId}", friendshipId.toString());

        EntityExchangeResult<String> entityExchangeResult = webTestClient.
                mutateWith(mockJwt().jwt(jwt)).post().uri(endpoint)
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk().expectBody(String.class)
                .returnResult();

        //the output must be consumed for the test case to work.
        LOG.info("response: {}", entityExchangeResult.getResponseBody());
        SeUserFriend seUserFriend = new SeUserFriend(userId, friendId, user.getFullName(), true, friendshipId);
        final String jsonSeUserFriend = Mapper.getJson(seUserFriend);
        LOG.info("jsonSeUserFriend {}", jsonSeUserFriend);

        assertThat(entityExchangeResult.getResponseBody()).isEqualTo("{\"message\":"+ jsonSeUserFriend +"}");

        // 1 userWebClient.findById call is for friendId
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/"+friendId);

        // 2
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/"+userId);

        // 3 notification.sendFriendNotification
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/notifications");

        Mono<Boolean> booleanMono1  =  friendshipRepository.existsById(friendshipId);
        booleanMono1.as(StepVerifier::create).assertNext(aBoolean -> {
            assertThat(aBoolean).isTrue();
            LOG.info("verified there is no friendship with id anymore");
        }).verifyComplete();

        Flux<Friendship> friendshipFlux = friendshipRepository.findByUserIdAndFriendId(userId, friendId);
        friendshipFlux.as(StepVerifier::create).assertNext(friendship -> {
            LOG.info("assert the new friendship created");
            assertThat(friendship.getId()).isNotNull();
            assertThat(friendship.getFriendId()).isEqualTo(friendId);
            assertThat(friendship.getUserId()).isEqualTo(userId);
            assertThat(friendship.getRequestAccepted()).isTrue();
            assertThat(friendship.getResponseSentDate()).isNotNull();
            assertThat(friendship.getRequestSentDate()).isNotNull();
            assertThat(friendship.isNew()).isFalse();
        }).verifyComplete();

        return booleanMono;
    }

    @Test
    public void cancelFriendship() throws InterruptedException {
        LOG.info("decline friendship test");

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        final String authenticationId = "dave";
        Jwt jwt = jwt(authenticationId, userId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        UUID friendId = UUID.randomUUID();
        LOG.info("friendId: {}",friendId);

        Friendship friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, false);
        friendshipRepository.save(friendship).subscribe();

        cancelFriendship(jwt, friendship.getId()).subscribe(aBoolean -> LOG.info("aBoolean is now {}", aBoolean));
    }

    private Mono<Boolean> cancelFriendship(Jwt jwt, UUID friendshipId) {
        LOG.info("decline friendship with id: {}", friendshipId);

        Mono<Boolean> booleanMono =  friendshipRepository.existsById(friendshipId);
        booleanMono.as(StepVerifier::create).assertNext(aBoolean -> {
            assertThat(aBoolean).isTrue();
            LOG.info("assert that boolean is true");

        }).verifyComplete();

        LOG.info("verified there is a friendship with this id");


        String endpoint = "/friendships/cancel/{friendshipId}".replace("{friendshipId}", friendshipId.toString());

        EntityExchangeResult<String> entityExchangeResult = webTestClient.
                mutateWith(mockJwt().jwt(jwt)).delete().uri(endpoint)
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk().expectBody(String.class)
                .returnResult();

        //the output must be consumed for the test case to work.
        LOG.info("response: {}", entityExchangeResult.getResponseBody());
        assertThat(entityExchangeResult.getResponseBody()).isEqualTo("{\"message\":\"friendship deleted by id\"}");

        Mono<Boolean> booleanMono1  =  friendshipRepository.existsById(friendshipId);
        booleanMono1.as(StepVerifier::create).assertNext(aBoolean -> {
            assertThat(aBoolean).isFalse();
            LOG.info("verified there is no friendship with id anymore");
        }).verifyComplete();
        return booleanMono;
    }

    @Test
    public void findFriendship() throws InterruptedException {
        LOG.info("find friendships");

        friendshipRepository.deleteAll().subscribe();

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        final String authenticationId = "dave";
        Jwt jwt = jwt(authenticationId, userId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        UUID friendId = null;
        final int count = 10;
        //for (int i = 0; i < count; i++) {

        friendId = UUID.randomUUID();
        Friendship friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        String friendFullName = "Shambha-"+friendId;
        User friend = new User(friendId, friendFullName);
        String jsonFriend = Mapper.getJson(friend);

        LOG.info("add jsonFriend body to mock queue");

        //1
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                    setResponseCode(201).setBody(jsonFriend));

        friendId = UUID.randomUUID();
        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        friendFullName = "Shambha-"+friendId;
        friend = new User(friendId, friendFullName);
        jsonFriend = Mapper.getJson(friend);

        //2
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        friendId = UUID.randomUUID();
        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        friendFullName = "Shambha-"+friendId;
        friend = new User(friendId, friendFullName);
        jsonFriend = Mapper.getJson(friend);
        //3
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        friendId = UUID.randomUUID();
        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        friendFullName = "Shambha-"+friendId;
        friend = new User(friendId, friendFullName);
        jsonFriend = Mapper.getJson(friend);
        //4
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        friendId = UUID.randomUUID();
        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        friendFullName = "Shambha-"+friendId;
        friend = new User(friendId, friendFullName);
        jsonFriend = Mapper.getJson(friend);
        //5
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        friendId = UUID.randomUUID();
        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        friendFullName = "Shambha-"+friendId;
        friend = new User(friendId, friendFullName);
        jsonFriend = Mapper.getJson(friend);
        //6
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        friendId = UUID.randomUUID();
        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        friendFullName = "Shambha-"+friendId;
        friend = new User(friendId, friendFullName);
        jsonFriend = Mapper.getJson(friend);
        //7
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        friendId = UUID.randomUUID();
        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        friendFullName = "Shambha-"+friendId;
        friend = new User(friendId, friendFullName);
        jsonFriend = Mapper.getJson(friend);
        //8
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        friendId = UUID.randomUUID();
        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        friendFullName = "Shambha-"+friendId;
        friend = new User(friendId, friendFullName);
        jsonFriend = Mapper.getJson(friend);
        //9
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));

        friendId = UUID.randomUUID();
        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        friendFullName = "Shambha-"+friendId;
        friend = new User(friendId, friendFullName);
        jsonFriend = Mapper.getJson(friend);
        //10
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").
                setResponseCode(201).setBody(jsonFriend));
        //}
        findFriendship(jwt, userId, count);//.subscribe();//subscribe(seUserFriend -> LOG.info("seUserFriend is {}", seUserFriend));
    }

    private void findFriendship(Jwt jwt, UUID userId, final int count) throws InterruptedException {
        LOG.info("find friendships for logged-in user");

        Flux<Friendship> friendshipFlux =  friendshipRepository.findAcceptedFriendsForUser(userId);

        StepVerifier.create(friendshipFlux).expectNextCount(count).verifyComplete();

        LOG.info("verified there are 10 friendships for the userId");

        String endpoint = "/friendships";

        Flux<SeUserFriend> seUserFriendFlux = webTestClient.
                mutateWith(mockJwt().jwt(jwt)).get().uri(endpoint)
                .headers(addJwt(jwt))
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange().expectStatus().isOk()
                .returnResult(SeUserFriend.class).getResponseBody();

        // 1 userWebClient.findById call is for friendId
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        //2
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        //3
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        //4
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        //5
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        //6
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        //7
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        //8
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        //9
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        //10
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/users/");

        // }


        LOG.info("assert count to be 10");

        StepVerifier.create(seUserFriendFlux).assertNext(seUserFriend -> {
            LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
            assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
        }).assertNext(seUserFriend -> {
                    LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
            assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
                }).assertNext(seUserFriend -> {
                    LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
            assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
                }).assertNext(seUserFriend -> {
                    LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
            assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
                }).assertNext(seUserFriend -> {
                    LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
            assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
                }).assertNext(seUserFriend -> {
                    LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
            assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
                }).assertNext(seUserFriend -> {
                    LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
            assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
                }).assertNext(seUserFriend -> {
                    LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
            assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
                }).assertNext(seUserFriend -> {
                    LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
           assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
                }).assertNext(seUserFriend -> {
                    LOG.info("assert the userId is the logged-in userId {}", seUserFriend.getUserId());
           assertThat(seUserFriend.getUserId()).isEqualTo(userId);
            assertThat(seUserFriend.getFriendId()).isNotNull();
            assertThat(seUserFriend.getFriendId()).isNotEqualTo(userId);
            assertThat(seUserFriend.getFriendshipId()).isNotNull();
            assertThat(seUserFriend.isFriend()).isTrue();
            assertThat(seUserFriend.getProfilePhoto()).isNull();
                })
                .verifyComplete();
    }

    @Test
    public void isFriends() throws InterruptedException {
        LOG.info("isFriends endpoint test");

        //for this set friendId to hardcoded value so that we can pass this friendId into jwt
        //which will accept the friendship, user cannot accept only the friend in the friendship can
        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        String authenticationId = "dave";
        Jwt jwt = jwt(authenticationId, userId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        UUID friendId = UUID.randomUUID();
        LOG.info("friendId: {}",friendId);

        Friendship friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), userId, friendId, true);
        friendshipRepository.save(friendship).subscribe();

        LOG.info("verify user {} is friend with {}", userId, friendId);
        isFriends(jwt, friendId, true);

        LOG.info("delete all friendships before testing another one");
        friendshipRepository.deleteAll().subscribe();

        // swap userId and friendId
        friendId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        authenticationId = "dave";
        jwt = jwt(authenticationId, friendId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        userId = UUID.randomUUID();
        LOG.info("friendId: {}",friendId);

        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), friendId, userId, true);
        friendshipRepository.save(friendship).subscribe();

        LOG.info("verify user {} is friend with {}", userId, friendId);
        isFriends(jwt, userId, true);

        LOG.info("delete all friendships before testing another one");
        friendshipRepository.deleteAll().subscribe();

        friendship = new Friendship(LocalDateTime.now(), LocalDateTime.now(), friendId, userId, false);
        friendshipRepository.save(friendship).subscribe();

        LOG.info("verify user {} is friend with {}", userId, friendId);
        isFriends(jwt, userId, false);
    }

    private void isFriends(Jwt jwt, UUID userId, boolean isFriends) {
        LOG.info("check user is friends userId: {}", userId);

        String endpoint = "/friendships/{userId}".replace("{userId}", userId.toString());

        EntityExchangeResult<String> entityExchangeResult = webTestClient.
                mutateWith(mockJwt().jwt(jwt)).get().uri(endpoint)
                .headers(addJwt(jwt))
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange().expectStatus().isOk().expectBody(String.class)
                .returnResult();

        LOG.info("response is {}", entityExchangeResult.getResponseBody());

        assertThat(entityExchangeResult.getResponseBody()).isEqualTo("{\"message\":"+isFriends+"}");
    }

    private Jwt jwt(String subjectName, UUID userId) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName, "userId", userId.toString()));
    }

    private Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }
}
