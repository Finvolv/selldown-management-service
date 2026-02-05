package com.finvolv.selldown.service;

import com.finvolv.selldown.model.OperationFileDataEntity;
import com.finvolv.selldown.repository.MonthlyOperationStatusRepository;
import com.finvolv.selldown.repository.OperationFileDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OperationExcelExportService {

    private final MonthlyOperationStatusRepository monthlyOperationStatusRepository;
    private final OperationFileDataRepository operationFileDataRepository;

    // Process Excel file by adding operation metadata columns
    public Mono<String> processOperationExcel(String filePath, Integer year, Integer month) {
        log.info("Processing operation Excel file: {}, year: {}, month: {}", filePath, year, month);
        
        // Step 1: Get monthly operation status ID
        return monthlyOperationStatusRepository.findByYearAndMonth(year, month)
            .switchIfEmpty(Mono.error(new RuntimeException(
                String.format("No operation status found for year: %d, month: %d", year, month))))
            .flatMap(operationStatus -> {
                Long operationStatusId = operationStatus.getId();
                log.info("Found operation status ID: {}", operationStatusId);
                
                // Step 2: Get all operation file data for this status
                return operationFileDataRepository.findByMonthlyOperationId(operationStatusId)
                    .collectList()
                    .flatMap(operationDataList -> {
                        log.info("Found {} operation data records", operationDataList.size());
                        
                        // Step 3: Create LAN to metadata map
                        Map<String, Map<String, Object>> lanToMetadataMap = operationDataList.stream()
                            .collect(Collectors.toMap(
                                OperationFileDataEntity::getLmsLan,
                                OperationFileDataEntity::getMetadata,
                                (existing, replacement) -> existing // Keep first if duplicate
                            ));
                        
                        // Step 4: Process Excel file
                        return Mono.fromCallable(() -> 
                            addMetadataToExcel(filePath, lanToMetadataMap)
                        );
                    });
            })
            .doOnSuccess(processedPath -> 
                log.info("Successfully processed operation Excel file: {}", processedPath))
            .doOnError(error -> 
                log.error("Error processing operation Excel file: {}", error.getMessage(), error));
    }

    // Add metadata columns to Excel file with specific column sequence and formulas
    private String addMetadataToExcel(String filePath, Map<String, Map<String, Object>> lanToMetadataMap) 
            throws IOException {
        log.info("Adding metadata to Excel file: {}", filePath);
        
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            
            if (sheet.getPhysicalNumberOfRows() == 0) {
                log.warn("Excel file is empty, skipping processing");
                return filePath;
            }
            
            // Step 1: Find LAN column index
            Row headerRow = sheet.getRow(1);
            int lanColumnIndex = findColumnIndex(headerRow, "LAN");
            
            if (lanColumnIndex == -1) {
                log.error("LAN column not found in Excel file");
                throw new RuntimeException("LAN column not found in Excel file");
            }
            
            log.info("Found LAN column at index: {}", lanColumnIndex);
            
            // Step 2: Find the Closing Future POS column (check W or Y only)
            String closingPosColumn = findClosingFuturePOSColumn(headerRow);
            if (closingPosColumn == null) {
                log.error("Closing Future POS column not found in columns W or Y");
                throw new RuntimeException("Closing Future POS (excluding Principal overdues) column not found in W or Y");
            }
            log.info("Found Closing Future POS column at: {}", closingPosColumn);
            
            // Step 3: Define ordered columns with their configuration
            // Format: [metadataKey, headerLabel, diffColumn (if applicable), diffLabel]
            List<ColumnConfig> orderedColumns = Arrays.asList(
                new ColumnConfig("opsOpeningPOS", "Ops Opening POS", null, null),
                new ColumnConfig(null, "Opening POS diff", "E", "Opening POS diff"),
                new ColumnConfig("opsPrincipal", "Ops Principal", null, null),
                new ColumnConfig(null, "Principal diff", "K", "Principal due diff"),
                new ColumnConfig("opsInterest", "Ops Interest", null, null),
                new ColumnConfig(null, "Interest diff", "L", "Interest due diff"),
                new ColumnConfig("opsEMI", "Ops EMI", null, null),
                new ColumnConfig(null, "EMI diff", "J", "EMI due diff"),
                new ColumnConfig("opsClosingPOS", "Ops Closing POS", null, null),
                new ColumnConfig(null, "Closing POS diff", closingPosColumn, "Closing POS diff")
            );
            
            // Step 4: Add new column headers
            int newColumnStartIndex = headerRow.getLastCellNum();
            int columnIndex = newColumnStartIndex;
            
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            // Add light green background color
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            for (ColumnConfig config : orderedColumns) {
                Cell cell = headerRow.createCell(columnIndex++);
                cell.setCellValue(config.headerLabel);
                cell.setCellStyle(headerStyle);
            }
            
            log.info("Added {} column headers starting at column index {}", orderedColumns.size(), newColumnStartIndex);
            
            // Step 4: Process each data row
            int matchedRows = 0;
            int unmatchedRows = 0;
            
            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                
                // Get LAN value from current row
                Cell lanCell = row.getCell(lanColumnIndex);
                if (lanCell == null) continue;
                
                String lanValue = getCellValueAsString(lanCell);
                if (lanValue == null || lanValue.trim().isEmpty()) continue;
                
                // Find matching metadata
                Map<String, Object> metadata = lanToMetadataMap.get(lanValue.trim());
                
                if (metadata != null) {
                    matchedRows++;
                    
                    // Add values and formulas according to column configuration
                    columnIndex = newColumnStartIndex;
                    for (ColumnConfig config : orderedColumns) {
                        Cell cell = row.createCell(columnIndex);
                        
                        if (config.metadataKey != null) {
                            // Data column - get value from metadata
                            Object value = metadata.get(config.metadataKey);
                            setCellValue(cell, value);
                        } else if (config.diffSourceColumn != null) {
                            // Diff column - create Excel formula
                            // Formula: currentColumn - sourceColumn
                            int previousColumnIndex = columnIndex - 1; // The ops column is just before the diff column
                            String previousColumnLetter = getColumnLetter(previousColumnIndex);
                            String formula = String.format("%s%d-%s%d", 
                                previousColumnLetter, rowIndex + 1, 
                                config.diffSourceColumn, rowIndex + 1);
                            
                            cell.setCellFormula(formula);
                        }
                        
                        columnIndex++;
                    }
                } else {
                    unmatchedRows++;
                    log.debug("No metadata found for LAN: {}", lanValue);
                }
            }
            
            log.info("Excel processing complete - Matched rows: {}, Unmatched rows: {}", 
                matchedRows, unmatchedRows);
            
            // Step 5: Save the modified workbook
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            
            log.info("Saved modified Excel file: {}", filePath);
            return filePath;
        }
    }
    
    // Convert column index to Excel column letter (0 -> A, 25 -> Z, 26 -> AA, etc.)
    private String getColumnLetter(int columnIndex) {
        StringBuilder columnLetter = new StringBuilder();
        while (columnIndex >= 0) {
            columnLetter.insert(0, (char) ('A' + (columnIndex % 26)));
            columnIndex = (columnIndex / 26) - 1;
        }
        return columnLetter.toString();
    }
    
    // Inner class to hold column configuration
    private static class ColumnConfig {
        String metadataKey;
        String headerLabel;
        String diffSourceColumn;
        String diffLabel;
        
        ColumnConfig(String metadataKey, String headerLabel, String diffSourceColumn, String diffLabel) {
            this.metadataKey = metadataKey;
            this.headerLabel = headerLabel;
            this.diffSourceColumn = diffSourceColumn;
            this.diffLabel = diffLabel;
        }
    }

    // Find column index by header name
    private int findColumnIndex(Row headerRow, String columnName) {
        if (headerRow == null) return -1;
        
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String cellValue = getCellValueAsString(cell);
                if (cellValue != null && cellValue.trim().equalsIgnoreCase(columnName.trim())) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    // Find "Closing Future POS (excluding Principal overdues)" column from columns W or Y only
    private String findClosingFuturePOSColumn(Row headerRow) {
        if (headerRow == null) return null;
        
        String targetHeader = "Closing Future POS (excluding Principal overdues)";
        
        // Check column W (index 22, since A=0)
        Cell cellW = headerRow.getCell(22);
        if (cellW != null) {
            String cellValue = getCellValueAsString(cellW);
            if (cellValue != null && cellValue.trim().equalsIgnoreCase(targetHeader.trim())) {
                log.info("Found Closing Future POS in column W");
                return "W";
            }
        }
        
        // Check column Y (index 24, since A=0)
        Cell cellY = headerRow.getCell(24);
        if (cellY != null) {
            String cellValue = getCellValueAsString(cellY);
            if (cellValue != null && cellValue.trim().equalsIgnoreCase(targetHeader.trim())) {
                log.info("Found Closing Future POS in column Y");
                return "Y";
            }
        }
        
        log.warn("Closing Future POS column not found in W or Y");
        return null;
    }

    // Get cell value as string
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Format as integer if it's a whole number
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    }
                    return String.valueOf(numValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    // Set cell value from object
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        
        if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
