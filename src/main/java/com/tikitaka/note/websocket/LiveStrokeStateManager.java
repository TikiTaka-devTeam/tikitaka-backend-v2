package com.tikitaka.note.websocket;

import com.tikitaka.note.entity.StrokeTool;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class LiveStrokeStateManager {

    private static final int MAX_POINTS_PER_CHUNK = 256;
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);
    private final ConcurrentHashMap<Key, State> states = new ConcurrentHashMap<>();

    public void accept(String sessionId, UUID spaceId, UUID slideId, LiveStrokeMessage message) {
        if (sessionId == null || message == null || message.type() == null
                || message.clientStrokeId() == null) {
            invalid();
        }
        Key key = new Key(sessionId, spaceId, slideId, message.clientStrokeId());
        switch (message.type()) {
            case STROKE_START -> start(key, message);
            case STROKE_POINTS -> points(key, message);
            case STROKE_END -> end(key, message);
            case STROKE_CANCEL -> cancel(key, message);
        }
    }

    public void markCommitted(UUID spaceId, UUID slideId, Set<UUID> clientStrokeIds) {
        if (clientStrokeIds.isEmpty()) {
            return;
        }
        states.keySet().removeIf(key -> key.spaceId().equals(spaceId)
                && key.slideId().equals(slideId)
                && clientStrokeIds.contains(key.clientStrokeId()));
    }

    private void start(Key key, LiveStrokeMessage message) {
        if (message.tool() != StrokeTool.PEN && message.tool() != StrokeTool.HIGHLIGHTER) invalid();
        if (message.color() == null || !message.color().matches("#[0-9a-fA-F]{6}")) invalid();
        if (!positiveFinite(message.thickness()) || !ratio(message.opacity())) invalid();
        if (message.strokeOrder() == null || message.strokeOrder() < 0) invalid();
        validatePoint(message.point());
        requireNull(message.chunkSeq(), message.points(), message.lastChunkSeq());
        State previous = states.putIfAbsent(key, new State(0, false, Instant.now()));
        if (previous != null) invalid();
    }

    private void points(Key key, LiveStrokeMessage message) {
        if (message.chunkSeq() == null || message.chunkSeq() < 1
                || message.points() == null || message.points().isEmpty()
                || message.points().size() > MAX_POINTS_PER_CHUNK) invalid();
        requireNull(message.tool(), message.color(), message.thickness(), message.opacity(),
                message.strokeOrder(), message.point(), message.lastChunkSeq());
        message.points().forEach(this::validatePoint);
        states.compute(key, (ignored, state) -> {
            if (state == null || state.ended() || message.chunkSeq() != state.lastChunkSeq() + 1) {
                return invalid();
            }
            return new State(message.chunkSeq(), false, Instant.now());
        });
    }

    private void end(Key key, LiveStrokeMessage message) {
        if (message.lastChunkSeq() == null || message.lastChunkSeq() < 0) invalid();
        requireNull(message.tool(), message.color(), message.thickness(), message.opacity(),
                message.strokeOrder(), message.point(), message.chunkSeq(), message.points());
        states.compute(key, (ignored, state) -> {
            if (state == null || state.ended() || message.lastChunkSeq() != state.lastChunkSeq()) {
                return invalid();
            }
            return new State(state.lastChunkSeq(), true, Instant.now());
        });
    }

    private void cancel(Key key, LiveStrokeMessage message) {
        requireNull(message.tool(), message.color(), message.thickness(), message.opacity(),
                message.strokeOrder(), message.point(), message.chunkSeq(), message.points(),
                message.lastChunkSeq());
        if (states.remove(key) == null) invalid();
    }

    private void validatePoint(LiveStrokeMessage.Point point) {
        if (point == null || !ratio(point.xRatio()) || !ratio(point.yRatio())) invalid();
    }

    private boolean positiveFinite(Double value) {
        return value != null && Double.isFinite(value) && value > 0;
    }

    private boolean ratio(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 1;
    }

    private void requireNull(Object... values) {
        for (Object value : values) {
            if (value != null) invalid();
        }
    }

    private <T> T invalid() {
        throw new MessageDeliveryException("WS_INVALID_STROKE_EVENT");
    }

    @Scheduled(fixedDelay = 60_000)
    void removeStaleStates() {
        Instant threshold = Instant.now().minus(STALE_AFTER);
        states.entrySet().removeIf(entry -> entry.getValue().updatedAt().isBefore(threshold));
    }

    @EventListener
    void onDisconnect(SessionDisconnectEvent event) {
        states.keySet().removeIf(key -> key.sessionId().equals(event.getSessionId()));
    }

    private record Key(String sessionId, UUID spaceId, UUID slideId, UUID clientStrokeId) {
    }

    private record State(int lastChunkSeq, boolean ended, Instant updatedAt) {
    }
}
