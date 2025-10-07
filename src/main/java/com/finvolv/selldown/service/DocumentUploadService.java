package com.finvolv.selldown.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class DocumentUploadService {

    private final WebClient webClient;
    private final String documentServiceBaseUrl;

    public DocumentUploadService(WebClient.Builder builder, @Value("${documentService.baseUrl}") String baseUrl) {
        this.documentServiceBaseUrl = baseUrl;
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<String> generateUploadId(String authorizationBearerToken,
                                         String partnerName,
                                         String dealName,
                                         Integer year,
                                         Integer month) {
        // Path format: {partnerName}/payout_sd-{dealName}-{month}-{year}
        String fileSegment = String.format("payout_sd-%s-%d-%d", dealName, month, year);
        String path = UriComponentsBuilder.fromPath("/loan-management-service/api/v1/document/generate/{partnerName}/{fileSegment}")
            .buildAndExpand(partnerName, fileSegment)
            .toUriString();

        Map<String, Object> payload = Map.of(
            "contextData", Map.of("fileType", "payout_sd"),
            "scope", java.util.List.of("DOWNLOAD", "UPLOAD", "VIEW"),
            "services", java.util.List.of("callbackservice"),
            "singleFileUpload", true
        );

        return webClient.post()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, authorizationBearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .exchangeToMono(clientResponse -> {
                if (clientResponse.statusCode().is2xxSuccessful()) {
                    return clientResponse.bodyToMono(Map.class)
                        .map(resp -> (String) resp.get("generatedId"));
                }
                return clientResponse.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new RuntimeException("Generate failed: " + clientResponse.statusCode() + " body=" + body)));
            });
    }

    public Mono<Void> uploadExcel(String authorizationBearerToken,
                                  String generatedId,
                                  byte[] bytes,
                                  String filename) {
        String path = String.format("/loan-management-service/api/v1/document/upload/%s", generatedId);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return webClient.post()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, authorizationBearerToken)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .retrieve()
            .bodyToMono(Void.class);
    }
}


