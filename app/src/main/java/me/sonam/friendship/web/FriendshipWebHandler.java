package me.sonam.friendship.web;

import me.sonam.friendship.FriendshipService;
import me.sonam.webclients.friendship.SeUserFriend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Service
public class FriendshipWebHandler implements FriendshipHandler {
    private static final Logger LOG = LoggerFactory.getLogger(FriendshipWebHandler.class);

    private final FriendshipService friendshipService;


    public FriendshipWebHandler(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @Override
    public Mono<ServerResponse> isFriends(ServerRequest serverRequest) {
        UUID friendId = UUID.fromString(serverRequest.pathVariable("userId"));

        return friendshipService.isFriends( friendId)
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("message", s)))
                .onErrorResume(throwable -> {
                    LOG.debug("exception occurred in isFriends method", throwable);
                    LOG.error("failed in isFriends method {}", throwable.getMessage());
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "isFriends() method failed with error: " + throwable.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> requestFriendshipWith(ServerRequest serverRequest) {
        LOG.debug("pathVariable userId: {}", serverRequest.pathVariable("userId"));
        UUID friendId = UUID.fromString(serverRequest.pathVariable("userId"));

        LOG.debug("request friendship with {}", friendId);

         return friendshipService.createFriendship(friendId).flatMap(s ->  ServerResponse.created(URI.create("/friendships/"+ s.getFriendshipId()))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(throwable -> {
                    LOG.debug("exception occurred in creating Friendship", throwable);
                    LOG.error("requestFriendship failed {}", throwable.getMessage());
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "requestFriendship failed with error: " + throwable.getMessage()));
                });
    }



    @Override
    public Mono<ServerResponse> declineFriendship(ServerRequest serverRequest) {
        UUID friendshipId = UUID.fromString(serverRequest.pathVariable("friendshipId"));
        LOG.debug("cancel friendship with friendship-id {}", friendshipId);

        return friendshipService.delete(friendshipId).flatMap(s -> {
            LOG.info("deleted friendship, returning server response for string: {}", s);
        return                ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s));
        })
                .onErrorResume(throwable -> {
                    LOG.debug("exception occurred in declining Friendship", throwable);
                    LOG.error("declineFriendship failed {}", throwable.getMessage());
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "declineFriendship failed with error: " + throwable.getMessage()));
                });
    }


    @Override
    public Mono<ServerResponse> acceptFriendship(ServerRequest serverRequest) {
        UUID friendshipId = UUID.fromString(serverRequest.pathVariable("friendshipId"));

        LOG.info("accept friendshipId {}", friendshipId);

        return friendshipService.acceptFriendship(friendshipId).flatMap(s ->  ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(throwable -> {
                    LOG.debug("exception occurred in accepting Friendship", throwable);
                    LOG.error("acceptFriendship failed {}", throwable.getMessage());
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "acceptFriendship failed with error: " + throwable.getMessage()));
                });
    }



    /**
     * This method is called when either user or friend wants to terminate the
     * friendship
     * @param serverRequest
     * @return
     */
    @Override
    public Mono<ServerResponse> cancelFriendship(ServerRequest serverRequest) {
        UUID friendshipId = UUID.fromString(serverRequest.pathVariable("friendshipId"));

        LOG.info("cancel friendship for friendshipId {}", friendshipId);

        return friendshipService.delete(friendshipId).flatMap(s ->  ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(throwable -> {
                    LOG.debug("exception occurred in cancel Friendship", throwable);
                    LOG.error("cancelFriendship failed {}", throwable.getMessage());
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "cancelFriendship failed with error: " + throwable.getMessage()));
                });
    }

    /**
     * returns a stream of SeUserFriend
     * @param serverRequest
     * @return
     */
    @Override
    public Mono<ServerResponse> findFriends(ServerRequest serverRequest) {
        //int page = Integer.parseInt(serverRequest.pathVariable("page"));
        //int size = Integer.parseInt(serverRequest.pathVariable("size"));

        LOG.info("findFriends from");
        return friendshipService.getLoggedInUserId()
                .map(friendshipService::getFriendships)
                .flatMap(s ->  ServerResponse.ok()
                .contentType(MediaType.APPLICATION_NDJSON).body(s, SeUserFriend.class))
                .onErrorResume(throwable -> {
                    LOG.debug("exception occurred in cancel Friendship", throwable);
                    LOG.error("cancelFriendship failed {}", throwable.getMessage());
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "cancelFriendship failed with error: " + throwable.getMessage()));
                });
    }


}
