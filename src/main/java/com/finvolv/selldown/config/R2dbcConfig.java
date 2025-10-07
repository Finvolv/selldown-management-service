package com.finvolv.selldown.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvolv.selldown.mapper.PostgresJsonToMapConverter;
import com.finvolv.selldown.mapper.PostgresMapToJsonConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.r2dbc.core.DatabaseClient;

import java.util.Arrays;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        var dialect = DialectResolver.getDialect(databaseClient.getConnectionFactory());
        return R2dbcCustomConversions.of(dialect, Arrays.asList(
                new PostgresJsonToMapConverter(objectMapper),
                new PostgresMapToJsonConverter(objectMapper)
        ));
    }
}
