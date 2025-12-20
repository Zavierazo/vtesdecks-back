package com.vtesdecks.messaging.configuration;

import com.vtesdecks.messaging.DefaultMessageDelegate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.RedisSerializer;


@Configuration
public class RedisConsumerConfiguration {

    @Bean
    public MessageListenerAdapter messageListenerAdapter(DefaultMessageDelegate defaultMessageDelegate) {
        return new MessageListenerAdapter(defaultMessageDelegate, "handleMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory, MessageListenerAdapter listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTopicSerializer(RedisSerializer.string());
        container.addMessageListener(listener, ChannelTopic.of("deck-sync"));
        return container;
    }
}
