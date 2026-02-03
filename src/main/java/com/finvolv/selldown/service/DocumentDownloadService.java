package com.finvolv.selldown.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentDownloadService {

    private final WebClient.Builder webClientBuilder;
    
    @Value("${documentService.baseUrlLoanTracking}")
    private String documentServiceBaseUrl;

    public Mono<byte[]> downloadPayoutFile(
        String authorizationBearerToken,
        String partnerName,
        String dealName,
        Integer year,
        Integer month,
        String documentId,
        String folderId
    ) {
        log.info("Downloading payout file - partnerName: {}, dealName: {}, year: {}, month: {}, documentId: {}, folderId: {}", 
            partnerName, dealName, year, month, documentId, folderId);
        
        WebClient webClient = webClientBuilder
            .baseUrl(documentServiceBaseUrl)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
            .clientConnector(new ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(60))
            ))
            .build();
        
        String fileSegment = String.format("payout_sd-%s-%d-%d", dealName, month, year);
        String generatePath = UriComponentsBuilder
            .fromPath("/loan-management-service/api/v1/document/generate/{partnerName}/{fileSegment}/{folderId}")
            .buildAndExpand(partnerName, fileSegment, folderId)
            .toUriString();
        
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, String> contextData = new HashMap<>();
        contextData.put("fileType", "PAYOUT_SD");
        requestBody.put("contextData", contextData);
        requestBody.put("scope", new String[]{"DOWNLOAD", "UPLOAD", "VIEW"});
        requestBody.put("services", new String[]{"callbackservice"});
        requestBody.put("singleFileUpload", true);
        log.info("Auth inside document download: {}", authorizationBearerToken);
//        String authorizationToken = "Bearer " + authorizationBearerToken;
        Mono<String> generateIdMono = webClient.post()
            .uri(generatePath)
            .header(HttpHeaders.AUTHORIZATION, authorizationBearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                String generatedId = (String) response.get("generatedId");
                log.info("Generated download ID: {}", generatedId);
                return generatedId;
            });
        
        return generateIdMono.flatMap(generatedId -> {
            String downloadPath = String.format("/loan-management-service/api/v1/document/%s/%s", 
                generatedId, documentId);
            
            log.info("Fetching document URL from: {}", downloadPath);
            
            return webClient.get()
                .uri(downloadPath)
                .header(HttpHeaders.AUTHORIZATION, authorizationBearerToken)
                .retrieve()
                .bodyToMono(Map.class)
                .map(downloadResponse -> {
                    log.info("Document response: {}", downloadResponse);
                    String documentUrl = (String) downloadResponse.get("documentUrl");
                    if (documentUrl == null || documentUrl.isBlank()) {
                        throw new RuntimeException("No document URL found in response");
                    }
                    log.info("Got document URL: {}", documentUrl);
                    return documentUrl;
                })
                .flatMap(documentUrl -> {
                    log.info("Downloading file from URL...");
                    
                    // Create a separate WebClient for S3 without base URL
                    // Use URI.create() to preserve the complete signed URL with all query parameters
                    WebClient s3WebClient = WebClient.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                        .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(Duration.ofSeconds(60))
                        ))
                        .build();
                    
                    return s3WebClient.get()
                        .uri(URI.create(documentUrl))  // URI.create() preserves the exact URL with query params
                        .retrieve()
                        .bodyToFlux(DataBuffer.class)
                        .collectList()
                        .map(dataBuffers -> {
                            int totalSize = dataBuffers.stream()
                                .mapToInt(DataBuffer::readableByteCount)
                                .sum();
                            
                            byte[] bytes = new byte[totalSize];
                            int currentIndex = 0;
                            
                            for (DataBuffer dataBuffer : dataBuffers) {
                                int count = dataBuffer.readableByteCount();
                                dataBuffer.read(bytes, currentIndex, count);
                                currentIndex += count;
                                DataBufferUtils.release(dataBuffer);
                            }
                            
                            return bytes;
                        });
                });
        })
        .doOnSuccess(bytes -> log.info("Successfully downloaded payout file, size: {} bytes", bytes.length))
        .doOnError(error -> log.error("Error downloading payout file: {}", error.getMessage(), error));
    }

    /**
     * Download payout file and save to temp location (similar to LoanTrackingService approach)
     */
    public Mono<String> downloadPayoutFileToTemp(
        String authorizationBearerToken,
        String partnerName,
        String dealName,
        Integer year,
        Integer month,
        String documentId,
        String folderId
    ) {
        log.info("Downloading payout file to temp - partnerName: {}, dealName: {}, year: {}, month: {}, documentId: {}, folderId: {}", 
            partnerName, dealName, year, month, documentId, folderId);
        
        return downloadPayoutFile(authorizationBearerToken, partnerName, dealName, year, month, documentId, folderId)
            .flatMap(bytes -> {
                try {
                    // Create temp file
                    String tempDir = System.getProperty("java.io.tmpdir");
                    String fileName = String.format("payout_%s_%s_%d_%d_%s.xlsx", 
                        partnerName, dealName, year, month, System.currentTimeMillis());
                    File tempFile = new File(tempDir, fileName);
                    
                    // Write bytes to temp file
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        fos.write(bytes);
                    }
                    
                    String tempFilePath = tempFile.getAbsolutePath();
                    log.info("Saved payout file to temp location: {}", tempFilePath);
                    return Mono.just(tempFilePath);
                } catch (IOException e) {
                    log.error("Error saving payout file to temp: {}", e.getMessage(), e);
                    return Mono.error(new RuntimeException("Failed to save payout file to temp: " + e.getMessage(), e));
                }
            });
    }

    /**
     * Cleanup temp file
     */
    public void cleanupTempFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists() && file.delete()) {
                log.info("Deleted temp file: {}", filePath);
            } else {
                log.warn("Failed to delete temp file or file doesn't exist: {}", filePath);
            }
        } catch (Exception e) {
            log.error("Error deleting temp file {}: {}", filePath, e.getMessage(), e);
        }
    }
}