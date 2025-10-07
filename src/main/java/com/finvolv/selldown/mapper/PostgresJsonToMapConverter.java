package com.finvolv.selldown.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@ReadingConverter
@Slf4j
public class PostgresJsonToMapConverter implements Converter<Json, Map<String,Object>> {

    private final ObjectMapper objectMapper;
    private final TypeReference<Map<String,Object>> typeRef = new TypeReference<Map<String,Object>>() {};

    public PostgresJsonToMapConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String,Object> convert(Json source) {
        if(source == null) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(source.asArray(), typeRef);
        } catch (IOException e) {
            log.error("Error while converting JSON to Map", e);
            throw new RuntimeException(e);
        }
    }

}
