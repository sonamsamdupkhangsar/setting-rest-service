package me.sonam.friendship.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

@Configuration
public class Router {
    private static final Logger LOG = LoggerFactory.getLogger(Router.class);

    @Bean
    public RouterFunction<ServerResponse> route(FriendshipHandler handler) {
        LOG.info("building router function");
        return RouterFunctions
                .route(POST("/friendships/request/{userId}"), handler::requestFriendshipWith)
                .andRoute(DELETE("/friendships/decline/{friendshipId}"), handler::declineFriendship)
                .andRoute(POST("/friendships/accept/{friendshipId}"), handler::acceptFriendship)
                .andRoute(DELETE("/friendships/cancel/{friendshipId}"), handler::cancelFriendship)
                .andRoute(GET("/friendships"), handler::findFriends)
                .andRoute(GET("/friendships/{userId}"), handler::isFriends);

    }
}