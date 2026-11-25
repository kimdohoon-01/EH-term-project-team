package com.cafe.menu;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
class MenuUpdates {
    private final Set<SseEmitter> subscribers = ConcurrentHashMap.newKeySet();

    SseEmitter subscribe() {
        var subscriber = new SseEmitter(Duration.ofMinutes(5).toMillis());
        subscriber.onCompletion(() -> subscribers.remove(subscriber));
        subscriber.onTimeout(() -> subscribers.remove(subscriber));
        subscriber.onError(error -> subscribers.remove(subscriber));
        subscribers.add(subscriber);
        send(subscriber, SseEmitter.event().comment("connected"));
        return subscriber;
    }

    void menuChanged() {
        broadcast(() -> SseEmitter.event().name("menu-changed").data("refresh"));
    }

    @Scheduled(fixedDelay = 15_000)
    void keepAlive() {
        broadcast(() -> SseEmitter.event().comment("keepalive"));
    }

    private void broadcast(Supplier<SseEmitter.SseEventBuilder> event) {
        for (SseEmitter subscriber : subscribers) {
            send(subscriber, event.get());
        }
    }

    private void send(SseEmitter subscriber, SseEmitter.SseEventBuilder event) {
        try {
            subscriber.send(event);
        } catch (IOException | IllegalStateException disconnected) {
            subscribers.remove(subscriber);
        }
    }
}
