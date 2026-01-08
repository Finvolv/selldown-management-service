package com.finvolv.selldown.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.math.BigDecimal;
import java.util.List;

@WritingConverter
@Slf4j
public class PostgresListToJsonConverter implements Converter<List<BigDecimal>, Json> {

    private final ObjectMapper objectMapper;

    public PostgresListToJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Json convert(List<BigDecimal> source) {
        try {
            if(source == null) {
                return Json.of("[]");
            }
            return Json.of(objectMapper.writeValueAsString(source));
        } catch (JsonProcessingException e) {
            log.error("Error while converting List<BigDecimal> to JSON", e);
            throw new RuntimeException(e);
        }
    }

}

