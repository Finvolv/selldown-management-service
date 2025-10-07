package com.finvolv.selldown.service;

import com.finvolv.selldown.model.PartnerPayoutDetailsAll;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {

    public byte[] buildPartnerPayoutReport(List<PartnerPayoutDetailsAll> payouts) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Partner Payout Report");

            Map<String, java.util.function.Function<PartnerPayoutDetailsAll, Object>> columns = buildColumns();

            // Header
            Row header = sheet.createRow(0);
            int colIdx = 0;
            for (String headerName : columns.keySet()) {
                Cell cell = header.createCell(colIdx++);
                cell.setCellValue(headerName);
            }

            // Rows
            int rowIdx = 1;
            for (PartnerPayoutDetailsAll p : payouts) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                for (java.util.function.Function<PartnerPayoutDetailsAll, Object> getter : columns.values()) {
                    Object value = getter.apply(p);
                    Cell cell = row.createCell(c++);
                    setCellValue(cell, value);
                }
            }

            // Autosize
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to build Excel file", e);
        }
    }

    public Path writeReportToFile(List<PartnerPayoutDetailsAll> payouts, String directory, String filename) {
        byte[] bytes = buildPartnerPayoutReport(payouts);
        try {
            Path dir = Paths.get(directory);
            Files.createDirectories(dir);
            Path filePath = dir.resolve(filename);
            Files.write(filePath, bytes);
            return filePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Excel file to disk", e);
        }
    }

    private Map<String, java.util.function.Function<PartnerPayoutDetailsAll, Object>> buildColumns() {
        Map<String, java.util.function.Function<PartnerPayoutDetailsAll, Object>> cols = new LinkedHashMap<>();

        // Non-seller
        cols.put("LAN", PartnerPayoutDetailsAll::getLmsLan);
        cols.put("Deal Rate", p -> "");
        cols.put("Opening POS without overdues", p -> safeSubtract(p.getOpeningPos(), p.getPrincipalOverdue()));
        cols.put("Closing Future POS (excluding Principal overdues)", PartnerPayoutDetailsAll::getClosingPos);
        cols.put("Principal due", PartnerPayoutDetailsAll::getTotalPrincipalDue);
        cols.put("Opening Principal Overdues", PartnerPayoutDetailsAll::getPrincipalOverdue);
        cols.put("Collection against overdue Principal", PartnerPayoutDetailsAll::getPrincipalOverduePaid);
        cols.put("Interest Due", PartnerPayoutDetailsAll::getTotalInterestDue);
        cols.put("Opening Interest Overdues", PartnerPayoutDetailsAll::getInterestOverdue);
        cols.put("Collection against overdue Interest", PartnerPayoutDetailsAll::getInterestOverduePaid);
        cols.put("Part Payment", PartnerPayoutDetailsAll::getPrepaymentPaid);
        cols.put("Foreclosure charges received (Exc of GST)", PartnerPayoutDetailsAll::getForeclosureChargesPaid);
        cols.put("Bounce charges received (Exc of GST)", p -> "");
        cols.put("Total Collections (EMI+Overdue+Part+F.C)", PartnerPayoutDetailsAll::getTotalPaid);
        cols.put("Closing DPD", PartnerPayoutDetailsAll::getClosingDpd);

        // Seller
        cols.put("Opening POS ( Without overdue) Sell down", PartnerPayoutDetailsAll::getSellerOpeningPos);
        cols.put("Closing Future POS (excluding Principal overdues)", PartnerPayoutDetailsAll::getSellerClosingPos);
        cols.put("Principal", PartnerPayoutDetailsAll::getSellerTotalPrincipalDue);
        cols.put("Principal O/d collection", PartnerPayoutDetailsAll::getSellerPrincipalOverduePaid);
        cols.put("Interest", PartnerPayoutDetailsAll::getSellerTotalInterestDue);
        cols.put("Overdue Interest  collection", PartnerPayoutDetailsAll::getSellerInterestOverduePaid);
        cols.put("Pre Payment", PartnerPayoutDetailsAll::getSellerPrepaymentPaid);
        cols.put("FC Charges", PartnerPayoutDetailsAll::getSellerForeclosureChargesPaid);
        cols.put("Total Collections (EMI+Overdue+Part+F.C)", PartnerPayoutDetailsAll::getSellerTotalPaid);

        return cols;
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private BigDecimal safeSubtract(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return left.subtract(right);
    }
}


