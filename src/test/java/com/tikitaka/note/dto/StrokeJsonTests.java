package com.tikitaka.note.dto;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import com.tikitaka.note.dto.request.StrokeSyncRequest.*;
import com.tikitaka.note.dto.response.*;
import com.tikitaka.note.dto.response.FixerResponse;
import com.tikitaka.note.entity.StrokeTool;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
class StrokeJsonTests {
    final JsonMapper mapper=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    @Test void operationPayloadSurvivesJsonbRoundtrip() {
        Payload payload=new Payload(Type.CREATE,new Stroke(UUID.randomUUID(),StrokeTool.PEN,
                List.of(new Point(0.1,0.2)),"#000000",2.0,1.0,0),null);
        assertThat(mapper.readValue(mapper.writeValueAsString(payload),Payload.class)).isEqualTo(payload);
    }
    @Test void booleanFieldsMatchSpec() {
        var stroke=new StrokeResponse(UUID.randomUUID(),StrokeTool.PEN,List.of(),"#000000",2.0,1.0,0,false);
        var json=mapper.readTree(mapper.writeValueAsString(stroke));
        assertThat(json.has("is_deleted")).isTrue(); assertThat(json.has("deleted")).isFalse();
        var checked=mapper.readTree(mapper.writeValueAsString(new FixerResponse.Checked(UUID.randomUUID(),true)));
        assertThat(checked.get("is_checked").asBoolean()).isTrue();
        assertThat(checked.has("checked")).isFalse();
    }
}