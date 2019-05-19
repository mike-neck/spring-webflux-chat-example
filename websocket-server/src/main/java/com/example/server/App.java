/*
 * Copyright 2019 Shinya Mochida
 *
 * Licensed under the Apache License,Version2.0(the"License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,software
 * Distributed under the License is distributed on an"AS IS"BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;
import reactor.util.Loggers;

import java.util.Map;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        Loggers.useSl4jLoggers();
        SpringApplication.run(App.class, args);
    }

    @Bean
    UnicastProcessor<Message> unicastProcessor() {
        return UnicastProcessor.create();
    }

    @Bean
    Flux<Message> subscribingChannel(UnicastProcessor<Message> unicastProcessor) {
        return unicastProcessor
                .replay(25)
                .autoConnect()
                .log(Loggers.getLogger(App.class));
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    HandlerMapping webSocketMapping(UnicastProcessor<Message> unicastProcessor, ObjectMapper objectMapper, Flux<Message> subscribingChannel) {
        ChatHandler chatHandler = new ChatHandler(unicastProcessor, objectMapper, subscribingChannel);
        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setUrlMap(Map.of("/chat", chatHandler));
        handlerMapping.setOrder(10);
        return handlerMapping;
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
