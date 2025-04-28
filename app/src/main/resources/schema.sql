
CREATE TABLE if not exists Friendship (id UUID PRIMARY KEY, request_sent_date timestamp,
 response_sent_date timestamp, request_accepted boolean, user_id UUID, friend_id UUID);
