package com.finvolv.selldown.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@ReadingConverter
@Slf4j
public class PostgresJsonToListConverter implements Converter<Json, List<BigDecimal>> {

    private final ObjectMapper objectMapper;
    private final TypeReference<List<BigDecimal>> typeRef = new TypeReference<List<BigDecimal>>() {};

    public PostgresJsonToListConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BigDecimal> convert(Json source) {
        if(source == null) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(source.asArray(), typeRef);
        } catch (IOException e) {
            log.error("Error while converting JSON array to List<BigDecimal>", e);
            throw new RuntimeException(e);
        }
    }

}

