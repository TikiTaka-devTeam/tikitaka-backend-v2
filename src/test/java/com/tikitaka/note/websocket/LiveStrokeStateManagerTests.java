package com.tikitaka.note.websocket;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tikitaka.note.entity.StrokeTool;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;

class LiveStrokeStateManagerTests {

    private final LiveStrokeStateManager manager = new LiveStrokeStateManager();
    private final String sessionId = "session";
    private final UUID spaceId = UUID.randomUUID();
    private final UUID slideId = UUID.randomUUID();
    private final UUID strokeId = UUID.randomUUID();

    @Test
    void acceptsStartPointsAndEndInOrder() {
        assertThatCode(() -> {
            manager.accept(sessionId, spaceId, slideId, start());
            manager.accept(sessionId, spaceId, slideId, points(1));
            manager.accept(sessionId, spaceId, slideId, end(1));
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsPointsWithoutStartAndSkippedChunkSequence() {
        assertThatThrownBy(() -> manager.accept(sessionId, spaceId, slideId, points(1)))
                .isInstanceOf(MessageDeliveryException.class);

        manager.accept(sessionId, spaceId, slideId, start());
        assertThatThrownBy(() -> manager.accept(sessionId, spaceId, slideId, points(2)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void cancelRemovesLiveStrokeAndPreventsFurtherEvents() {
        manager.accept(sessionId, spaceId, slideId, start());
        manager.accept(sessionId, spaceId, slideId, cancel());
        assertThatThrownBy(() -> manager.accept(sessionId, spaceId, slideId, end(0)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private LiveStrokeMessage start() {
        return new LiveStrokeMessage(LiveStrokeMessage.Type.STROKE_START, strokeId,
                StrokeTool.PEN, "#000000", 2.0, 1.0, 1,
                new LiveStrokeMessage.Point(0.1, 0.2), null, null, null);
    }

    private LiveStrokeMessage points(int sequence) {
        return new LiveStrokeMessage(LiveStrokeMessage.Type.STROKE_POINTS, strokeId,
                null, null, null, null, null, null, sequence,
                List.of(new LiveStrokeMessage.Point(0.2, 0.3)), null);
    }

    private LiveStrokeMessage end(int sequence) {
        return new LiveStrokeMessage(LiveStrokeMessage.Type.STROKE_END, strokeId,
                null, null, null, null, null, null, null, null, sequence);
    }

    private LiveStrokeMessage cancel() {
        return new LiveStrokeMessage(LiveStrokeMessage.Type.STROKE_CANCEL, strokeId,
                null, null, null, null, null, null, null, null, null);
    }
}
