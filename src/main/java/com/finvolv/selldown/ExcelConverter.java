package com.finvolv.selldown;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Simple standalone Java program to convert horizontal Excel data to vertical format
 * 
 * File Path: "Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx"
 * Sheet Name: "I"
 * LAN Column: "LAN"
 * 
 * Usage: java ExcelConverter <filePath> <sheetName> [lanColumnName]
 * Example: java ExcelConverter "Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx" "I" "LAN"
 */
public class ExcelConverter {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ExcelConverter <filePath> <sheetName> [lanColumnName]");
            System.out.println("Example: java ExcelConverter \"Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx\" \"I\" \"LAN\"");
            System.exit(1);
        }

        String filePath = args[0];
        String sheetName = args[1];
        String lanColumnName = args.length > 2 ? args[2] : "LAN";

        System.out.println("=== Excel Horizontal to Vertical Converter ===");
        System.out.println("File Path: " + filePath);
        System.out.println("Sheet Name: " + sheetName);
        System.out.println("LAN Column Name: " + lanColumnName);
        System.out.println();

        try {
            String outputPath = convertHorizontalToVertical(filePath, sheetName, lanColumnName);
            System.out.println("✅ Conversion completed successfully!");
            System.out.println("📁 Output file saved to: " + outputPath);
        } catch (Exception e) {
            System.err.println("❌ Error during conversion: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Converts horizontal Excel data to vertical format
     */
    public static String convertHorizontalToVertical(String filePath, String sheetName, String lanColumnName) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(inputStream);
             Workbook outputWorkbook = new XSSFWorkbook()) {
            
            Sheet inputSheet = workbook.getSheet(sheetName);
            if (inputSheet == null) {
                throw new RuntimeException("Sheet '" + sheetName + "' not found in the Excel file");
            }
            
            System.out.println("📖 Reading sheet: " + sheetName);
            
            // Create output sheet
            Sheet outputSheet = outputWorkbook.createSheet("Converted Data");
            
            // Process the data
            List<List<Object>> convertedData = processHorizontalData(inputSheet, lanColumnName);
            
            System.out.println("🔄 Converting " + (convertedData.size() - 1) + " data rows...");
            
            // Write converted data to output sheet
            writeVerticalData(outputSheet, convertedData);
            
            // Save the output file
            String outputPath = saveOutputFile(outputWorkbook, filePath);
            
            return outputPath;
        }
    }
    
    /**
     * Processes horizontal data and converts it to vertical format
     */
    private static List<List<Object>> processHorizontalData(Sheet sheet, String lanColumnName) {
        List<List<Object>> convertedData = new ArrayList<>();
        
        // Find the header row (might not be at row 0)
        Row headerRow = findHeaderRow(sheet, lanColumnName);
        if (headerRow == null) {
            throw new RuntimeException("LAN column '" + lanColumnName + "' not found in any row of the sheet");
        }
        
        int lanColumnIndex = findColumnIndex(headerRow, lanColumnName);
        int headerRowIndex = headerRow.getRowNum();
        
        System.out.println("📍 Found header row at index: " + headerRowIndex);
        System.out.println("📍 Found LAN column at index: " + lanColumnIndex);
        
        // Get all column headers (excluding LAN column) and format them
        List<String> monthColumns = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            if (i != lanColumnIndex) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String headerValue = formatMonthHeader(cell);
                    if (headerValue != null && !headerValue.trim().isEmpty()) {
                        monthColumns.add(headerValue);
                    }
                }
            }
        }
        
        System.out.println("📅 Found " + monthColumns.size() + " month columns: " + monthColumns);
        
        // Add header row to output
        List<Object> headerRowData = Arrays.asList("LAN", "Month", "Value");
        convertedData.add(headerRowData);
        
        // Process each data row (start from the row after header)
        int processedRows = 0;
        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            
            // Get LAN value
            Cell lanCell = row.getCell(lanColumnIndex);
            String lanValue = getCellValueAsString(lanCell);
            if (lanValue == null || lanValue.trim().isEmpty()) {
                continue; // Skip rows without LAN value
            }
            
            processedRows++;
            
            // Convert each month column to a separate row (skip zero values)
            int monthColumnIndex = 0;
            for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                if (colIndex != lanColumnIndex) {
                    Cell valueCell = row.getCell(colIndex);
                    Object cellValue = getCellValue(valueCell);
                    
                    // Skip rows with zero or null values
                    if (cellValue != null && !isZeroValue(cellValue)) {
                        // Create a new row for this LAN-Month combination
                        List<Object> newRow = Arrays.asList(
                            lanValue,
                            monthColumns.get(monthColumnIndex),
                            cellValue
                        );
                        convertedData.add(newRow);
                    }
                    monthColumnIndex++;
                }
            }
        }
        
        System.out.println("📊 Processed " + processedRows + " LAN records");
        
        return convertedData;
    }
    
    /**
     * Finds the header row that contains the LAN column
     */
    private static Row findHeaderRow(Sheet sheet, String lanColumnName) {
        // Search through the first 10 rows to find the header row
        for (int rowIndex = 0; rowIndex <= Math.min(9, sheet.getLastRowNum()); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                    Cell cell = row.getCell(colIndex);
                    if (cell != null) {
                        String cellValue = getCellValueAsString(cell);
                        if (lanColumnName.equalsIgnoreCase(cellValue)) {
                            System.out.println("🔍 Found LAN column '" + lanColumnName + "' in row " + rowIndex);
                            return row;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Finds the column index for a given column name
     */
    private static int findColumnIndex(Row headerRow, String columnName) {
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String cellValue = getCellValueAsString(cell);
                if (columnName.equalsIgnoreCase(cellValue)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * Formats month header to DD-MMM-YYYY format
     */
    private static String formatMonthHeader(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy");
                    return formatter.format(date).toUpperCase();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    /**
     * Checks if a value is zero or null
     */
    private static boolean isZeroValue(Object value) {
        if (value == null) return true;
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue() == 0.0;
        }
        
        if (value instanceof String) {
            String str = ((String) value).trim();
            return str.isEmpty() || str.equals("0") || str.equals("0.0");
        }
        
        return false;
    }
    
    /**
     * Gets cell value as string
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    /**
     * Gets cell value as object
     */
    private static Object getCellValue(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                } else {
                    return cell.getNumericCellValue();
                }
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    /**
     * Writes vertical data to the output sheet
     */
    private static void writeVerticalData(Sheet sheet, List<List<Object>> data) {
        for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            List<Object> rowData = data.get(rowIndex);
            
            for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                Cell cell = row.createCell(colIndex);
                Object value = rowData.get(colIndex);
                setCellValue(cell, value);
            }
        }
        
        // Auto-size columns
        for (int i = 0; i < 3; i++) { // LAN, Month, Value columns
            sheet.autoSizeColumn(i);
        }
    }
    
    /**
     * Sets cell value based on object type
     */
    private static void setCellValue(Cell cell, Object value) {
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
            cell.setCellValue(String.valueOf(value));
        }
    }
    
    /**
     * Saves the output file to resources folder
     */
    private static String saveOutputFile(Workbook workbook, String originalFilePath) throws IOException {
        // Create resources directory if it doesn't exist
        Path resourcesDir = Paths.get("src/main/resources/excel-outputs");
        Files.createDirectories(resourcesDir);
        
        // Generate output filename
        String originalFileName = Paths.get(originalFilePath).getFileName().toString();
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String outputFileName = baseName + "_converted_" + System.currentTimeMillis() + ".xlsx";
        
        // Save the file
        Path outputPath = resourcesDir.resolve(outputFileName);
        try (FileOutputStream outputStream = new FileOutputStream(outputPath.toFile())) {
            workbook.write(outputStream);
        }
        
        return outputPath.toString();
    }
}
