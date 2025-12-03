package com.finvolv.selldown;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;

public class ListSheets {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ListSheets <filePath>");
            System.exit(1);
        }

        String filePath = args[0];
        System.out.println("Listing sheets in: " + filePath);
        System.out.println();

        try (FileInputStream inputStream = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            int sheetCount = workbook.getNumberOfSheets();
            System.out.println("Total sheets: " + sheetCount);
            System.out.println();
            
            for (int i = 0; i < sheetCount; i++) {
                String sheetName = workbook.getSheetName(i);
                System.out.println((i + 1) + ". " + sheetName);
            }
            
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
