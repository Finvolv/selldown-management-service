package com.finvolv.selldown.service;

import com.finvolv.selldown.model.LoanDetail;
import com.finvolv.selldown.model.PartnerPayoutDetailsAll;
import com.finvolv.selldown.repository.LoanDetailRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExcelExportService {

    private final PartnerPayoutDetailsAllService partnerPayoutDetailsAllService;
    private final LoanDetailRepository loanDetailRepository;

    public ExcelExportService(PartnerPayoutDetailsAllService partnerPayoutDetailsAllService, 
                             LoanDetailRepository loanDetailRepository) {
        this.partnerPayoutDetailsAllService = partnerPayoutDetailsAllService;
        this.loanDetailRepository = loanDetailRepository;
    }

    public Mono<byte[]> buildPartnerPayoutReport(List<PartnerPayoutDetailsAll> payouts, Long dealId, Long partnerId) {
        // Fetch deal to get the deal rate
        // Use a sentinel value to represent null, then convert back to null in the zip
        Mono<Double> dealRateMono = dealId != null
                ? partnerPayoutDetailsAllService.getDealById(dealId)
                        .map(deal -> deal != null && deal.getAnnualInterestRate() != null 
                                ? deal.getAnnualInterestRate() 
                                : Double.NaN) // Use NaN as sentinel for null
                        .defaultIfEmpty(Double.NaN) // Use NaN as sentinel for empty
                : Mono.just(Double.NaN); // Use NaN as sentinel for null

        // Fetch loan details to get Customer ROI (currentInterestRate) mapped by lmsLan
        Mono<Map<String, Double>> customerRoiMapMono = (dealId != null && partnerId != null)
                ? loanDetailRepository.findByDealIdAndPartnerId(dealId, partnerId)
                        .collectList()
                        .map(loanDetails -> {
                            if (loanDetails != null && !loanDetails.isEmpty()) {
                                return loanDetails.stream()
                                        .filter(loan -> loan.getLmsLan() != null && loan.getCurrentInterestRate() != null)
                                        .collect(Collectors.toMap(
                                                LoanDetail::getLmsLan,
                                                LoanDetail::getCurrentInterestRate,
                                                (existing, replacement) -> existing // Keep first if duplicate
                                        ));
                            }
                            return new LinkedHashMap<String, Double>();
                        })
                        .defaultIfEmpty(new LinkedHashMap<>())
                : Mono.just(new LinkedHashMap<>());

        // Combine both reactive operations and build the Excel file
        return Mono.zip(dealRateMono, customerRoiMapMono)
                .map(tuple -> {
                    Double dealRate = tuple.getT1();
                    // Convert NaN sentinel back to null
                    if (dealRate != null && dealRate.isNaN()) {
                        dealRate = null;
                    }
                    Map<String, Double> customerRoiMap = tuple.getT2();
                    
                    try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        Sheet sheet = workbook.createSheet("Partner Payout Report");

                        Map<String, java.util.function.Function<PartnerPayoutDetailsAll, Object>> columns = buildColumns(dealRate, customerRoiMap);
                        
                        // Create cell styles for gray and yellow backgrounds with bold font for header
                        XSSFFont boldFont = workbook.createFont();
                        boldFont.setBold(true);
                        
                        XSSFCellStyle grayStyle = workbook.createCellStyle();
                        grayStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                        grayStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        grayStyle.setFont(boldFont);
                        
                        XSSFCellStyle yellowStyle = workbook.createCellStyle();
                        yellowStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                        yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        yellowStyle.setFont(boldFont);
                        
                        // Identify seller column start index
                        int sellerStartIndex = -1;
                        int colIdx = 0;
                        for (String headerName : columns.keySet()) {
                            if (headerName.startsWith("Opening POS ( Without overdue) Sell down")) {
                                sellerStartIndex = colIdx;
                                break;
                            }
                            colIdx++;
                        }
                        
                        // If seller columns not found, use last column as fallback
                        if (sellerStartIndex == -1) {
                            sellerStartIndex = columns.size();
                        }

                        // Sum row (row 0) - add SUM formulas for numeric columns (no coloring)
                        Row sumRow = sheet.createRow(0);
                        colIdx = 0;
                        int dataStartRow = 2; // Data starts at row 2 (after header row 1)
                        int dataEndRow = dataStartRow + payouts.size() - 1;
                        
                        for (String headerName : columns.keySet()) {
                            Cell sumCell = sumRow.createCell(colIdx);
                            
                            // Add SUM formula for numeric columns (skip text columns like LAN)
                            if (colIdx > 0 && !headerName.equals("LAN") && !headerName.equals("Deal Rate") 
                                    && !headerName.equals("Customer ROI") && !headerName.equals("Closing DPD")) {
                                String columnLetter = getColumnLetter(colIdx);
                                String formula = String.format("SUM(%s%d:%s%d)", columnLetter, dataStartRow + 1, columnLetter, dataEndRow + 1);
                                sumCell.setCellFormula(formula);
                            } else {
                                sumCell.setCellValue(""); // Empty for non-numeric columns
                            }
                            colIdx++;
                        }

                        // Header row (row 1)
                        Row header = sheet.createRow(1);
                        colIdx = 0;
                        for (String headerName : columns.keySet()) {
                            Cell cell = header.createCell(colIdx);
                            cell.setCellValue(headerName);
                            
                            // Apply color based on column position
                            if (colIdx < sellerStartIndex) {
                                cell.setCellStyle(grayStyle);
                            } else {
                                cell.setCellStyle(yellowStyle);
                            }
                            colIdx++;
                        }

                        // Data rows (starting from row 2) - no coloring
                        int rowIdx = dataStartRow;
                        for (PartnerPayoutDetailsAll p : payouts) {
                            Row row = sheet.createRow(rowIdx++);
                            int c = 0;
                            for (java.util.function.Function<PartnerPayoutDetailsAll, Object> getter : columns.values()) {
                                Object value = getter.apply(p);
                                Cell cell = row.createCell(c);
                                setCellValue(cell, value);
                                // No styling applied to data rows
                                c++;
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
                });
    }

    public Mono<Path> writeReportToFile(List<PartnerPayoutDetailsAll> payouts, String directory, String filename, Long dealId, Long partnerId) {
        return buildPartnerPayoutReport(payouts, dealId, partnerId)
                .map(bytes -> {
                    try {
                        Path dir = Paths.get(directory);
                        Files.createDirectories(dir);
                        Path filePath = dir.resolve(filename);
                        Files.write(filePath, bytes);
                        return filePath;
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write Excel file to disk", e);
                    }
                });
    }

    private Map<String, java.util.function.Function<PartnerPayoutDetailsAll, Object>> buildColumns(Double dealRate, Map<String, Double> customerRoiMap) {
        Map<String, java.util.function.Function<PartnerPayoutDetailsAll, Object>> cols = new LinkedHashMap<>();

        // Non-seller
        cols.put("LAN", PartnerPayoutDetailsAll::getLmsLan);
        cols.put("Deal Rate", p -> dealRate != null ? dealRate : "");
        cols.put("Customer ROI", p -> {
            if (p.getLmsLan() != null && customerRoiMap.containsKey(p.getLmsLan())) {
                return customerRoiMap.get(p.getLmsLan());
            }
            return "";
        });
        
        cols.put("Opening POS without overdues", p -> safeSubtract(p.getOpeningPos(), p.getPrincipalOverdue()));
        cols.put("Opening Principal Overdues", PartnerPayoutDetailsAll::getPrincipalOverdue);
        cols.put("Opening Interest Overdues", PartnerPayoutDetailsAll::getInterestOverdue);
        cols.put("Opening Total Overdues", p -> safeAdd(p.getPrincipalOverdue(), p.getInterestOverdue()));
        cols.put("Opening Future POS including overdues 100%", PartnerPayoutDetailsAll::getOpeningPos);
        cols.put("EMI Due", p -> safeAdd(
                safeSubtract(p.getTotalPrincipalDue(), p.getPrincipalOverdue()),
                safeSubtract(p.getTotalInterestDue(), p.getInterestOverdue())));
        cols.put("Principal due",  p -> safeSubtract(p.getTotalPrincipalDue(), p.getPrincipalOverdue())); //Todo Check
        cols.put("Interest Due", PartnerPayoutDetailsAll::getTotalInterestDue); //TOdo
        cols.put("Collection against current Principal", p -> safeSubtract(p.getTotalPrincipalComponentPaid(), p.getPrincipalOverduePaid()));
        cols.put("Collection against current Interest", p -> safeSubtract(p.getTotalInterestComponentPaid(), p.getInterestOverduePaid()));
        cols.put("Collection against overdue Principal", PartnerPayoutDetailsAll::getPrincipalOverduePaid);
        cols.put("Collection against overdue Interest", PartnerPayoutDetailsAll::getInterestOverduePaid);
        cols.put("Part Payment", p -> safeAdd(p.getForeclosurePaid(),p.getPrepaymentPaid())); //Todo Plus foreclurePaid
        cols.put(" Closing Principal overdue", p -> safeSubtract(p.getTotalPrincipalDue(), p.getTotalPrincipalComponentPaid()));
        cols.put("Closing Intrest overdue", p -> safeSubtract(p.getTotalInterestDue(), p.getTotalInterestComponentPaid()));
        cols.put("Closing Total Overdues", p -> safeAdd(
                safeSubtract(p.getTotalPrincipalDue(), p.getTotalPrincipalComponentPaid()),
                safeSubtract(p.getTotalInterestDue(), p.getTotalInterestComponentPaid())));
        cols.put("Foreclosure charges received (Exc of GST)", PartnerPayoutDetailsAll::getForeclosureChargesPaid);
        cols.put("Bounce charges received (Exc of GST)", p -> safeSubtract(p.getTotalChargesPaid(), p.getForeclosureChargesPaid()));
        cols.put("Closing DPD", PartnerPayoutDetailsAll::getClosingDpd);
        cols.put("Total Collections (EMI+Overdue+Part+F.C)", PartnerPayoutDetailsAll::getTotalPaid);
        cols.put("Closing Future POS (excluding Principal overdues)", PartnerPayoutDetailsAll::getClosingPos); //ToDo Check

        // Seller Fields
        cols.put("Opening POS ( Without overdue) Sell down", p -> safeSubtract(p.getSellerOpeningPos(), p.getSellerPrincipalOverdue()));
        cols.put("Principal", p -> safeSubtract(p.getSellerTotalPrincipalComponentPaid(), p.getSellerPrincipalOverduePaid()));
        cols.put("Interest", p -> safeSubtract(p.getSellerTotalInterestDue(), p.getSellerInterestOverdue()));
        cols.put("Principal O/d collection", PartnerPayoutDetailsAll::getSellerPrincipalOverduePaid);
        cols.put("Overdue Interest  collection", PartnerPayoutDetailsAll::getSellerInterestOverduePaid); //ToDO
        cols.put("Pre Payment",p -> safeAdd(p.getSellerForeclosurePaid(),p.getSellerPrepaymentPaid()));
        cols.put("Bounce Charges", p -> safeSubtract(p.getSellerTotalChargesPaid(), p.getSellerForeclosureChargesPaid()));
        cols.put("FC Charges", PartnerPayoutDetailsAll::getSellerForeclosureChargesPaid);
        cols.put("Closing Future POS (excluding Principal overdues).",  p -> safeSubtract(p.getSellerClosingPos(), safeSubtract(p.getSellerTotalPrincipalDue(),p.getSellerTotalPrincipalComponentPaid())));
        cols.put("Total Collections (EMI+Overdue+Part+F.C).", PartnerPayoutDetailsAll::getSellerTotalPaid);
        // Closing Overdue Interest Need to find logic

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

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return left.add(right);
    }

    /**
     * Converts a column index (0-based) to Excel column letter (A, B, C, ..., Z, AA, AB, ...)
     */
    private String getColumnLetter(int columnIndex) {
        StringBuilder columnLetter = new StringBuilder();
        columnIndex++; // Convert to 1-based
        
        while (columnIndex > 0) {
            columnIndex--;
            columnLetter.insert(0, (char) ('A' + (columnIndex % 26)));
            columnIndex /= 26;
        }
        
        return columnLetter.toString();
    }
}


