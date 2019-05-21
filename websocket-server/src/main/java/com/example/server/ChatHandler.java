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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class ChatHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatHandler.class);

    private final FluxProcessor<Message, Message> messageProcessor;
    private final ObjectMapper objectMapper;
    private final Flux<Message> subscribingChannel;

    ChatHandler(FluxProcessor<Message, Message> messageProcessor, ObjectMapper objectMapper, Flux<Message> subscribingChannel) {
        this.messageProcessor = messageProcessor;
        this.objectMapper = objectMapper;
        this.subscribingChannel = subscribingChannel;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        logger.info("new connection, id:{}, address:{}, uri: {}, attr[type:{}]:{}",
                session.getId(),
                session.getHandshakeInfo().getRemoteAddress(),
                session.getHandshakeInfo().getUri(),
                session.getAttributes().getClass().getSimpleName(),
                session.getAttributes());
        Flux<Message> messages = session.receive()
                .map(webSocketMessage -> webSocketMessage.getPayloadAsText(StandardCharsets.UTF_8))
                .map(this::decode)
                .doOnNext(message -> logger.info("new message from: {}, message: {}", session.getId(), message))
                .doOnNext(this::onNext)
                .doOnError(this::onError)
                .doOnComplete(this::onComplete)
                .doOnCancel(this::onCancel)
                .onErrorResume(UncheckedIOException.class, e -> Mono.just(new Message("failure json")));
        return session.send(
                Flux.merge(subscribingChannel, messages)
                .map(this::encode)
                .map(session::textMessage));
    }

    private Message decode(String json) {
        try {
            return objectMapper.readValue(json, Message.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to decode json [" + json + "]", e);
        }
    }

    private String encode(Message message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("failed to encode json [" + message + "]", e);
        }
    }

    private void onNext(Message message) {
        logger.info("receive message {}", message);
        messageProcessor.onNext(message);
    }

    private void onError(Throwable error) {
        logger.error("error on receiving message, error: {}, message: {}", error.getClass(), error.getMessage(), error);
    }

    private void onComplete() {
        logger.info("on complete");
    }

    private void onCancel() {
        logger.info("on cancel");
    }
}
