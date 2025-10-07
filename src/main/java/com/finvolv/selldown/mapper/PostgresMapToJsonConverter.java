package com.finvolv.selldown.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.util.Map;

@WritingConverter
@Slf4j
public class PostgresMapToJsonConverter implements Converter<Map<String, Object>, Json> {

    private final ObjectMapper objectMapper;

    public PostgresMapToJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Json convert(Map<String, Object> source) {
        try {
            if(source == null) {
                return Json.of("{}");
            }
            return Json.of(objectMapper.writeValueAsString(source));
        } catch (JsonProcessingException e) {
            log.error("Error while converting Map to JSON", e);
            throw new RuntimeException(e);
        }
    }

}
