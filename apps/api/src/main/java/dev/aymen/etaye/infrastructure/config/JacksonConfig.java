package dev.aymen.etaye.infrastructure.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    public static class StrictLocalDateDeserializer extends ValueDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            if (!p.hasToken(JsonToken.VALUE_STRING)) {
                ctxt.reportInputMismatch(LocalDate.class,
                        "Expected String for LocalDate, but got %s", p.currentToken());
                return null;
            }
            String text = p.getString().trim();
            try {
                return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                ctxt.reportInputMismatch(LocalDate.class,
                        "Cannot parse date of birth: %s", text);
                return null;
            }
        }
    }

    @Bean
    public JsonMapperBuilderCustomizer strictTypeJsonMapperCustomizer() {
        return builder -> {
            builder.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
            builder.withCoercionConfig(LogicalType.Textual, cfg -> {
                cfg.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.Array, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.Object, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.Binary, CoercionAction.Fail);
            });
            SimpleModule dateModule = new SimpleModule("StrictLocalDateModule");
            dateModule.addDeserializer(LocalDate.class, new StrictLocalDateDeserializer());
            builder.addModule(dateModule);
        };
    }
}
