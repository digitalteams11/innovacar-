package com.carrental.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Jackson counterpart to {@link UtcDateTimeUtil#toIsoUtc} — for entities
 * serialized directly (not via a hand-built response map), annotate the
 * field with {@code @JsonSerialize(using = UtcLocalDateTimeSerializer.class)}
 * so it round-trips through the same UTC-stamped format instead of Jackson's
 * offset-less JSR-310 default.
 */
public class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(UtcDateTimeUtil.toIsoUtc(value));
        }
    }
}
