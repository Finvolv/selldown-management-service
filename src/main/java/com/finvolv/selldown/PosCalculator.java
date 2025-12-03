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
 * Excel converter that adds Opening POS and Closing POS calculations
 * 
 * File Path: "Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx"
 * Sheet Name: "I"
 * LAN Column: "LAN"
 * 
 * Features:
 * - Opening POS: Sum of all principal values for each LAN
 * - Closing POS: Opening POS - Principal for each month
 * - Next month Opening POS = Previous month Closing POS
 * 
 * Usage: java PosCalculator <filePath> <sheetName> [lanColumnName]
 * Example: java PosCalculator "Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx" "I" "LAN"
 */
public class PosCalculator {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java PosCalculator <filePath> <sheetName> [lanColumnName]");
            System.out.println("Example: java PosCalculator \"Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx\" \"I\" \"LAN\"");
            System.exit(1);
        }

        String filePath = args[0];
        String sheetName = args[1];
        String lanColumnName = args.length > 2 ? args[2] : "LAN";

        System.out.println("=== Excel POS Calculator ===");
        System.out.println("File Path: " + filePath);
        System.out.println("Sheet Name: " + sheetName);
        System.out.println("LAN Column Name: " + lanColumnName);
        System.out.println();

        try {
            String outputPath = calculatePosValues(filePath, sheetName, lanColumnName);
            System.out.println("✅ POS calculation completed successfully!");
            System.out.println("📁 Output file saved to: " + outputPath);
        } catch (Exception e) {
            System.err.println("❌ Error during POS calculation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Calculates Opening POS and Closing POS for each LAN
     */
    public static String calculatePosValues(String filePath, String sheetName, String lanColumnName) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(inputStream);
             Workbook outputWorkbook = new XSSFWorkbook()) {
            
            Sheet inputSheet = workbook.getSheet(sheetName);
            if (inputSheet == null) {
                throw new RuntimeException("Sheet '" + sheetName + "' not found in the Excel file");
            }
            
            System.out.println("📖 Reading sheet: " + sheetName);
            
            // Create output sheet
            Sheet outputSheet = outputWorkbook.createSheet("POS Calculated Data");
            
            // Process the data
            List<List<Object>> calculatedData = processPosData(inputSheet, lanColumnName);
            
            System.out.println("🔄 Processing " + calculatedData.size() + " data rows...");
            
            // Write calculated data to output sheet
            writePosData(outputSheet, calculatedData);
            
            // Save the output file
            String outputPath = saveOutputFile(outputWorkbook, filePath);
            
            return outputPath;
        }
    }
    
    /**
     * Processes vertical data and calculates POS values
     */
    private static List<List<Object>> processPosData(Sheet sheet, String lanColumnName) {
        List<List<Object>> calculatedData = new ArrayList<>();
        
        // Find the header row
        Row headerRow = findHeaderRow(sheet, lanColumnName);
        if (headerRow == null) {
            throw new RuntimeException("LAN column '" + lanColumnName + "' not found in any row of the sheet");
        }
        
        int lanColumnIndex = findColumnIndex(headerRow, lanColumnName);
        int headerRowIndex = headerRow.getRowNum();
        
        System.out.println("📍 Found header row at index: " + headerRowIndex);
        System.out.println("📍 Found LAN column at index: " + lanColumnIndex);
        
        // Print all available column names for debugging
        System.out.println("📋 Available columns in the Excel file:");
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String columnName = getCellValueAsString(cell);
                System.out.println("   Column " + i + ": " + columnName);
            }
        }
        
        // Find other column indices with flexible naming
        int monthColumnIndex = findColumnIndex(headerRow, "Month");
        if (monthColumnIndex == -1) {
            monthColumnIndex = findColumnIndex(headerRow, "month");
        }
        if (monthColumnIndex == -1) {
            monthColumnIndex = findColumnIndex(headerRow, "MONTH");
        }
        
        int principalColumnIndex = findColumnIndex(headerRow, "Principal");
        if (principalColumnIndex == -1) {
            principalColumnIndex = findColumnIndex(headerRow, "principal");
        }
        if (principalColumnIndex == -1) {
            principalColumnIndex = findColumnIndex(headerRow, "PRINCIPAL");
        }
        
        int interestColumnIndex = findColumnIndex(headerRow, "Interest");
        if (interestColumnIndex == -1) {
            interestColumnIndex = findColumnIndex(headerRow, "interest");
        }
        if (interestColumnIndex == -1) {
            interestColumnIndex = findColumnIndex(headerRow, "INTEREST");
        }
        
        System.out.println("📍 Found Month column at index: " + monthColumnIndex);
        System.out.println("📍 Found Principal column at index: " + principalColumnIndex);
        System.out.println("📍 Found Interest column at index: " + interestColumnIndex);
        
        // Validate that all required columns are found
        if (monthColumnIndex == -1) {
            throw new RuntimeException("Month column not found. Please check the column name in your Excel file.");
        }
        if (principalColumnIndex == -1) {
            throw new RuntimeException("Principal column not found. Please check the column name in your Excel file.");
        }
        if (interestColumnIndex == -1) {
            throw new RuntimeException("Interest column not found. Please check the column name in your Excel file.");
        }
        
        // Add header row to output (preserve existing columns + add POS columns)
        List<Object> headerRowData = Arrays.asList("LAN", "Month", "Principal", "Interest", "Opening POS", "Closing POS");
        calculatedData.add(headerRowData);
        
        // Group data by LAN
        Map<String, List<Map<String, Object>>> lanData = groupVerticalDataByLan(sheet, lanColumnIndex, monthColumnIndex, principalColumnIndex, interestColumnIndex, headerRowIndex);
        
        System.out.println("📊 Found " + lanData.size() + " unique LANs");
        
        // Process each LAN
        for (Map.Entry<String, List<Map<String, Object>>> entry : lanData.entrySet()) {
            String lanValue = entry.getKey();
            List<Map<String, Object>> monthlyData = entry.getValue();
            
            // Calculate total principal for Opening POS
            double totalPrincipal = calculateTotalPrincipal(monthlyData);
            
            System.out.println("💰 LAN " + lanValue + " - Total Principal: " + totalPrincipal);
            
            // Process each month for this LAN
            double currentOpeningPos = totalPrincipal;
            
            for (Map<String, Object> monthData : monthlyData) {
                String month = (String) monthData.get("month");
                Object principalObj = monthData.get("principal");
                Object interestObj = monthData.get("interest");
                
                double principal = 0.0;
                if (principalObj instanceof Number) {
                    principal = ((Number) principalObj).doubleValue();
                } else if (principalObj instanceof String) {
                    try {
                        principal = Double.parseDouble((String) principalObj);
                    } catch (NumberFormatException e) {
                        principal = 0.0;
                    }
                }
                
                double interest = 0.0;
                if (interestObj instanceof Number) {
                    interest = ((Number) interestObj).doubleValue();
                } else if (interestObj instanceof String) {
                    try {
                        interest = Double.parseDouble((String) interestObj);
                    } catch (NumberFormatException e) {
                        interest = 0.0;
                    }
                }
                
                // Calculate Closing POS
                double closingPos = currentOpeningPos - principal;
                
                // Add row to output (preserve all existing data + add POS)
                List<Object> rowData = Arrays.asList(
                    lanValue,
                    month,
                    principal,
                    interest,
                    currentOpeningPos,
                    closingPos
                );
                calculatedData.add(rowData);
                
                // Next month's Opening POS = Current month's Closing POS
                currentOpeningPos = closingPos;
            }
        }
        
        return calculatedData;
    }
    
    /**
     * Groups vertical data by LAN (for already vertical input format)
     */
    private static Map<String, List<Map<String, Object>>> groupVerticalDataByLan(Sheet sheet, int lanColumnIndex, int monthColumnIndex, int principalColumnIndex, int interestColumnIndex, int headerRowIndex) {
        Map<String, List<Map<String, Object>>> lanData = new LinkedHashMap<>();
        
        // Process each data row
        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            
            // Get LAN value
            Cell lanCell = row.getCell(lanColumnIndex);
            String lanValue = getCellValueAsString(lanCell);
            if (lanValue == null || lanValue.trim().isEmpty()) {
                continue;
            }
            
            // Get Month value
            Cell monthCell = row.getCell(monthColumnIndex);
            String monthValue = getCellValueAsString(monthCell);
            if (monthValue == null || monthValue.trim().isEmpty()) {
                continue;
            }
            
            // Get Principal value
            Cell principalCell = row.getCell(principalColumnIndex);
            Object principalValue = getCellValue(principalCell);
            
            // Get Interest value
            Cell interestCell = row.getCell(interestColumnIndex);
            Object interestValue = getCellValue(interestCell);
            
            // Initialize LAN data if not exists
            if (!lanData.containsKey(lanValue)) {
                lanData.put(lanValue, new ArrayList<>());
            }
            
            // Create month data
            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", monthValue);
            monthData.put("principal", principalValue);
            monthData.put("interest", interestValue);
            
            lanData.get(lanValue).add(monthData);
        }
        
        return lanData;
    }
    
    /**
     * Groups data by LAN (for horizontal input format)
     */
    private static Map<String, List<Map<String, Object>>> groupDataByLan(Sheet sheet, int lanColumnIndex, int headerRowIndex, List<String> monthColumns) {
        Map<String, List<Map<String, Object>>> lanData = new LinkedHashMap<>();
        
        // Process each data row
        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            
            // Get LAN value
            Cell lanCell = row.getCell(lanColumnIndex);
            String lanValue = getCellValueAsString(lanCell);
            if (lanValue == null || lanValue.trim().isEmpty()) {
                continue;
            }
            
            // Initialize LAN data if not exists
            if (!lanData.containsKey(lanValue)) {
                lanData.put(lanValue, new ArrayList<>());
            }
            
            // Process each month column
            int monthColumnIndex = 0;
            for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                if (colIndex != lanColumnIndex) {
                    // Check if we have a corresponding month column
                    if (monthColumnIndex < monthColumns.size()) {
                        Cell valueCell = row.getCell(colIndex);
                        Object cellValue = getCellValue(valueCell);
                        
                        // Process all values (including zero values to preserve data)
                        Map<String, Object> monthData = new HashMap<>();
                        monthData.put("month", monthColumns.get(monthColumnIndex));
                        monthData.put("principal", cellValue);
                        
                        // For now, set interest to 0 (you can modify this if you have interest data)
                        monthData.put("interest", 0.0);
                        
                        lanData.get(lanValue).add(monthData);
                    }
                    monthColumnIndex++;
                }
            }
        }
        
        return lanData;
    }
    
    /**
     * Calculates total principal for a LAN (sum of all principal values)
     */
    private static double calculateTotalPrincipal(List<Map<String, Object>> monthlyData) {
        double total = 0.0;
        for (Map<String, Object> monthData : monthlyData) {
            Object principal = monthData.get("principal");
            if (principal instanceof Number) {
                total += ((Number) principal).doubleValue();
            } else if (principal instanceof String) {
                try {
                    total += Double.parseDouble((String) principal);
                } catch (NumberFormatException e) {
                    // Skip invalid numbers
                }
            }
        }
        return total;
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
                    // Preserve original date format by using the cell's display value
                    return cell.toString();
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
     * Writes POS data to the output sheet
     */
    private static void writePosData(Sheet sheet, List<List<Object>> data) {
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
        for (int i = 0; i < 6; i++) { // LAN, Month, Principal, Interest, Opening POS, Closing POS
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
        String outputFileName = baseName + "_pos_calculated_" + System.currentTimeMillis() + ".xlsx";
        
        // Save the file
        Path outputPath = resourcesDir.resolve(outputFileName);
        try (FileOutputStream outputStream = new FileOutputStream(outputPath.toFile())) {
            workbook.write(outputStream);
        }
        
        return outputPath.toString();
    }
}
