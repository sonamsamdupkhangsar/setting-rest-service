package me.sonam.friendship.web;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public interface FriendshipHandler {
    Mono<ServerResponse> requestFriendshipWith(ServerRequest serverRequest);//UUID userId);
    Mono<ServerResponse> declineFriendship(ServerRequest serverRequest);//UUID friendshipId);
    Mono<ServerResponse> acceptFriendship(ServerRequest serverRequest);//UUID friendshipId);
    Mono<ServerResponse> cancelFriendship(ServerRequest serverRequest);//UUID friendshipId);
    Mono<ServerResponse> findFriends(ServerRequest serverRequest);//int page, int size);
    Mono<ServerResponse> isFriends(ServerRequest serverRequest);
}
