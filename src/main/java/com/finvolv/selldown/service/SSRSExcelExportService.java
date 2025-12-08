package com.finvolv.selldown.service;

import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.model.PartnerPayoutDetailsAll;
import com.finvolv.selldown.model.SSRSFileDataEntity;
import com.finvolv.selldown.repository.MonthlyLMSStatusRepository;
import com.finvolv.selldown.repository.PartnerPayoutDetailsAllRepository;
import com.finvolv.selldown.service.PartnerPayoutDetailsAllService;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SSRSExcelExportService {

    private final PartnerPayoutDetailsAllRepository partnerPayoutDetailsAllRepository;
    private final MonthlyLMSStatusRepository monthlyLMSStatusRepository;
    private final PartnerPayoutDetailsAllService partnerPayoutDetailsAllService;

    public SSRSExcelExportService(PartnerPayoutDetailsAllRepository partnerPayoutDetailsAllRepository,
                                 MonthlyLMSStatusRepository monthlyLMSStatusRepository,
                                 PartnerPayoutDetailsAllService partnerPayoutDetailsAllService) {
        this.partnerPayoutDetailsAllRepository = partnerPayoutDetailsAllRepository;
        this.monthlyLMSStatusRepository = monthlyLMSStatusRepository;
        this.partnerPayoutDetailsAllService = partnerPayoutDetailsAllService;
    }

    public Mono<byte[]> buildSSRSReport(List<SSRSFileDataEntity> ssrsData, Integer year, Integer month, Long dealId) {
        // Fetch deal information first
        Mono<Deal> dealMono = dealId != null 
            ? partnerPayoutDetailsAllService.getDealById(dealId)
            : Mono.just(null);
        
        // Fetch payout data for the same year and month via lmsId
        return Mono.zip(
            dealMono,
            monthlyLMSStatusRepository.findByYearAndMonth(year, month)
                .flatMapMany(lmsStatus -> 
                    partnerPayoutDetailsAllRepository.findByLmsId(lmsStatus.getId())
                )
                .collectList()
                .switchIfEmpty(Mono.just(java.util.Collections.<PartnerPayoutDetailsAll>emptyList()))
        )
        .map(tuple -> {
            Deal deal = tuple.getT1();
            List<PartnerPayoutDetailsAll> payoutDataList = tuple.getT2();
            // Create a map of payout data by lmsLan for quick lookup
            Map<String, PartnerPayoutDetailsAll> payoutMap = payoutDataList.stream()
                .filter(p -> p.getLmsLan() != null)
                .collect(Collectors.toMap(
                    PartnerPayoutDetailsAll::getLmsLan,
                    p -> p,
                    (existing, replacement) -> existing
                ));

            try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                // Sheet 1: Main reconciliation sheet
                Sheet sheet = workbook.createSheet("POS Validation Working Sheet + Charges");

                // Create color styles with exact colors matching the image
                XSSFFont boldFont = workbook.createFont();
                boldFont.setBold(true);
                boldFont.setColor(IndexedColors.WHITE.getIndex()); // White text for headers
                
                // Create exact color objects for reuse
                // Green: Light green/teal for LAN (RGB: 146, 208, 80 - Excel standard light green)
                org.apache.poi.xssf.usermodel.XSSFColor greenColor = new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)146, (byte)208, (byte)80}, null);
                
                // Blue: Medium blue for Status, BS ITD, and Payout columns (RGB: 68, 114, 196 - Excel standard blue)
                org.apache.poi.xssf.usermodel.XSSFColor blueColor = new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)68, (byte)114, (byte)196}, null);
                
                // Sandal: Light brown/beige for calculation columns (RGB: 217, 225, 242 - but this seems too blue)
                // Better sandal/beige color: RGB: 218, 150, 148 or RGB: 255, 242, 204 or RGB: 230, 184, 183
                // Using a more accurate sandal color: RGB: 230, 184, 183 (light brown/beige)
                org.apache.poi.xssf.usermodel.XSSFColor sandalColor = new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)230, (byte)184, (byte)183}, null);
                
                // Green style for LAN header
                XSSFCellStyle greenHeaderStyle = workbook.createCellStyle();
                greenHeaderStyle.setFillForegroundColor(greenColor);
                greenHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                greenHeaderStyle.setFont(boldFont);
                greenHeaderStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                greenHeaderStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
                greenHeaderStyle.setWrapText(true);
                greenHeaderStyle.setIndention((short) 1); // Add padding
                
                // Medium Blue style for headers
                XSSFCellStyle blueHeaderStyle = workbook.createCellStyle();
                blueHeaderStyle.setFillForegroundColor(blueColor);
                blueHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                blueHeaderStyle.setFont(boldFont);
                blueHeaderStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                blueHeaderStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
                blueHeaderStyle.setWrapText(true);
                blueHeaderStyle.setIndention((short) 1); // Add padding
                
                // Sandal/Beige style for headers
                XSSFCellStyle sandalHeaderStyle = workbook.createCellStyle();
                sandalHeaderStyle.setFillForegroundColor(sandalColor);
                sandalHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                sandalHeaderStyle.setFont(boldFont);
                sandalHeaderStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                sandalHeaderStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
                sandalHeaderStyle.setWrapText(true);
                sandalHeaderStyle.setIndention((short) 1); // Add padding
                
                // Data cell styles (white background, proper alignment)
                XSSFCellStyle greenDataStyle = createWhiteDataCellStyle(workbook, 
                    org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);
                XSSFCellStyle blueDataStyle = createWhiteDataCellStyle(workbook, 
                    org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
                XSSFCellStyle sandalDataStyle = createWhiteDataCellStyle(workbook, 
                    org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
                XSSFCellStyle centerDataStyle = createWhiteDataCellStyle(workbook, 
                    org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                
                // Red style for negative values in Overdue check (using custom RGB for light red)
                XSSFCellStyle redDataStyle = workbook.createCellStyle();
                org.apache.poi.xssf.usermodel.XSSFColor lightRedColor = new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)255, (byte)200, (byte)200}, null);
                redDataStyle.setFillForegroundColor(lightRedColor);
                redDataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                redDataStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
                redDataStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

                // Row 0: SUM row
                Row sumRow = sheet.createRow(0);
                int dataStartRow = 2; // Data starts at row 2 (after header row 1)
                int dataEndRow = dataStartRow + ssrsData.size() - 1;
                
                // Column indices
                int colLAN = 0; // B (0-indexed, but Excel shows as B)
                int colStatus = 1; // C
                int colBSFtmBeginningPR90 = 2; // D - BS OPENING Principle receivable 90%
                int colBSITDEnd = 3; // E
                int colPrincipalDA = 4; // F
                int colVDPR = 5; // G
                int colPLFtmDebt90 = 6; // H
                int colPLFtmBadDebtRecovery90 = 7; // I
                int colPLFtmSettlementLoss90 = 8; // J
                int colTotalVD = 9; // K
                int colPayoutReport = 10; // L
                int colOverduePR = 11; // M
                int colPartPaymentFC = 12; // N
                int colTotalPayout = 13; // O
                int colDiff = 14; // P
                int colOverdueCheck = 15; // Q
                int colPrincipalRemarks = 16; // R
                int colEmpty1 = 17; // S - Empty column
                int colEmpty2 = 18; // T - Empty column
                // First set of 4 columns for Bounce Charges
                int colPlFtmInstructBounceCharges90 = 19; // U - Bounce charges DA
                int colPayoutBounceCharges = 20; // V - Payout Bounce Charges
                int colDiffBounceCharges = 21; // W - Diff bounce charges
                int colRemarksBounceCharges = 22; // X - Remarks bounce charges
                // Second set of 4 columns for Foreclosure Charges
                int colPlFtmForeclosureCharges90 = 23; // Y - Foreclosure charges DA
                int colPayoutForeclosureCharges = 24; // Z - Payout foreclosure charges
                int colDiffForeclosureCharges = 25; // AA - Diff Foreclosure charges
                int colRemarksForeclosureCharges = 26; // AB - Remarks Foreclosure charges

                // SUM row formulas (row 0)
                sumRow.createCell(colLAN).setCellValue("SUM");
                sumRow.createCell(colStatus).setCellValue("");
                // SUM formulas for numeric columns
                setSumFormula(sumRow, colBSITDEnd, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colPrincipalDA, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colVDPR, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colPLFtmDebt90, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colPLFtmBadDebtRecovery90, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colPLFtmSettlementLoss90, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colTotalVD, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colPayoutReport, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colOverduePR, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colPartPaymentFC, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colTotalPayout, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colDiff, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colOverdueCheck, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colBSFtmBeginningPR90, dataStartRow, dataEndRow);
                // Empty columns
                sumRow.createCell(colEmpty1).setCellValue("");
                sumRow.createCell(colEmpty2).setCellValue("");
                // Bounce Charges columns
                setSumFormula(sumRow, colPlFtmInstructBounceCharges90, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colPayoutBounceCharges, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colDiffBounceCharges, dataStartRow, dataEndRow);
                sumRow.createCell(colRemarksBounceCharges).setCellValue("");
                // Foreclosure Charges columns
                setSumFormula(sumRow, colPlFtmForeclosureCharges90, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colPayoutForeclosureCharges, dataStartRow, dataEndRow);
                setSumFormula(sumRow, colDiffForeclosureCharges, dataStartRow, dataEndRow);
                sumRow.createCell(colRemarksForeclosureCharges).setCellValue("");

                // Header row (row 1) - set height for text wrapping and more space
                Row header = sheet.createRow(1);
                header.setHeightInPoints(35); // Taller row for wrapped text and more spacious
                
                Cell lanHeader = header.createCell(colLAN);
                lanHeader.setCellValue("LAN");
                lanHeader.setCellStyle(greenHeaderStyle);
                
                Cell statusHeader = header.createCell(colStatus);
                statusHeader.setCellValue("Status");
                statusHeader.setCellStyle(blueHeaderStyle);
                
                Cell bsFtmBeginningPRHeader = header.createCell(colBSFtmBeginningPR90);
                bsFtmBeginningPRHeader.setCellValue("BS  OPENING Principle receivable 90%");
                bsFtmBeginningPRHeader.setCellStyle(blueHeaderStyle);
                
                Cell bsItdHeader = header.createCell(colBSITDEnd);
                bsItdHeader.setCellValue("BS ITD End Principle receivable 90%");
                bsItdHeader.setCellStyle(blueHeaderStyle);
                
                Cell principalDAHeader = header.createCell(colPrincipalDA);
                principalDAHeader.setCellValue("Principal DA");
                principalDAHeader.setCellStyle(sandalHeaderStyle);
                
                Cell vdprHeader = header.createCell(colVDPR);
                vdprHeader.setCellValue("VD PR");
                vdprHeader.setCellStyle(sandalHeaderStyle);

                Cell plFtmDebt90Header = header.createCell(colPLFtmDebt90);
                plFtmDebt90Header.setCellValue("PL FTM Bad Debt 90");
                plFtmDebt90Header.setCellStyle(sandalHeaderStyle);

                Cell plFtmBadDebtRecovery90Header = header.createCell(colPLFtmBadDebtRecovery90);
                plFtmBadDebtRecovery90Header.setCellValue("PL FTM Bad Debt Recovery 90");
                plFtmBadDebtRecovery90Header.setCellStyle(sandalHeaderStyle);

                Cell plFtmSettlementLoss90Header = header.createCell(colPLFtmSettlementLoss90);
                plFtmSettlementLoss90Header.setCellValue("PL FTM Settlement Loss 90");
                plFtmSettlementLoss90Header.setCellStyle(sandalHeaderStyle);
                
                Cell totalVDHeader = header.createCell(colTotalVD);
                totalVDHeader.setCellValue("Total VD");
                totalVDHeader.setCellStyle(sandalHeaderStyle);
                
                Cell payoutReportHeader = header.createCell(colPayoutReport);
                payoutReportHeader.setCellValue("Payout Report");
                payoutReportHeader.setCellStyle(blueHeaderStyle);
                
                Cell overduePRHeader = header.createCell(colOverduePR);
                overduePRHeader.setCellValue("Overdue PR");
                overduePRHeader.setCellStyle(blueHeaderStyle);
                
                Cell partPaymentFCHeader = header.createCell(colPartPaymentFC);
                partPaymentFCHeader.setCellValue("Part Payment/FC");
                partPaymentFCHeader.setCellStyle(blueHeaderStyle);
                
                Cell totalPayoutHeader = header.createCell(colTotalPayout);
                totalPayoutHeader.setCellValue("Total payout");
                totalPayoutHeader.setCellStyle(blueHeaderStyle);
                
                Cell diffHeader = header.createCell(colDiff);
                diffHeader.setCellValue("Diff");
                diffHeader.setCellStyle(sandalHeaderStyle);
                
                Cell overdueCheckHeader = header.createCell(colOverdueCheck);
                overdueCheckHeader.setCellValue("Overdue check");
                overdueCheckHeader.setCellStyle(sandalHeaderStyle);

                Cell principalRemarksHeader = header.createCell(colPrincipalRemarks);
                principalRemarksHeader.setCellValue("Principal Remarks");
                principalRemarksHeader.setCellStyle(sandalHeaderStyle);
                
                // Empty columns
                Cell empty1Header = header.createCell(colEmpty1);
                empty1Header.setCellValue("");
                empty1Header.setCellStyle(sandalHeaderStyle);
                
                Cell empty2Header = header.createCell(colEmpty2);
                empty2Header.setCellValue("");
                empty2Header.setCellStyle(sandalHeaderStyle);
                
                // First set: Bounce Charges columns
                Cell plFtmInstructBounceCharges90Header = header.createCell(colPlFtmInstructBounceCharges90);
                plFtmInstructBounceCharges90Header.setCellValue("Bounce charges DA");
                plFtmInstructBounceCharges90Header.setCellStyle(sandalHeaderStyle);
                
                Cell payoutBounceChargesHeader = header.createCell(colPayoutBounceCharges);
                payoutBounceChargesHeader.setCellValue("Payout Bounce Charges");
                payoutBounceChargesHeader.setCellStyle(blueHeaderStyle);
                
                Cell diffBounceChargesHeader = header.createCell(colDiffBounceCharges);
                diffBounceChargesHeader.setCellValue("Diff bounce charges");
                diffBounceChargesHeader.setCellStyle(sandalHeaderStyle);
                
                Cell remarksBounceChargesHeader = header.createCell(colRemarksBounceCharges);
                remarksBounceChargesHeader.setCellValue("Remarks");
                remarksBounceChargesHeader.setCellStyle(sandalHeaderStyle);
                
                // Second set: Foreclosure Charges columns
                Cell plFtmForeclosureCharges90Header = header.createCell(colPlFtmForeclosureCharges90);
                plFtmForeclosureCharges90Header.setCellValue("Foreclosure charges DA");
                plFtmForeclosureCharges90Header.setCellStyle(sandalHeaderStyle);
                
                Cell payoutForeclosureChargesHeader = header.createCell(colPayoutForeclosureCharges);
                payoutForeclosureChargesHeader.setCellValue("Payout foreclosure charges");
                payoutForeclosureChargesHeader.setCellStyle(blueHeaderStyle);
                
                Cell diffForeclosureChargesHeader = header.createCell(colDiffForeclosureCharges);
                diffForeclosureChargesHeader.setCellValue("Diff Foreclosure charges");
                diffForeclosureChargesHeader.setCellStyle(sandalHeaderStyle);
                
                Cell remarksForeclosureChargesHeader = header.createCell(colRemarksForeclosureCharges);
                remarksForeclosureChargesHeader.setCellValue("Remarks");
                remarksForeclosureChargesHeader.setCellStyle(sandalHeaderStyle);

                // Data rows (starting from row 2)
                int rowIdx = dataStartRow;
                for (SSRSFileDataEntity ssrs : ssrsData) {
                    Row row = sheet.createRow(rowIdx);
                    row.setHeightInPoints(22); // Taller rows for more spacious cells
                    PartnerPayoutDetailsAll payout = payoutMap.get(ssrs.getLmsLan());
                    
                    // LAN (green background for data cells, left-aligned text)
                    Cell lanCell = row.createCell(colLAN);
                    lanCell.setCellValue(ssrs.getLmsLan() != null ? ssrs.getLmsLan() : "");
                    lanCell.setCellStyle(greenDataStyle);
                    
                    // Status Active Closed (white background, center-aligned text)
                    Cell statusCell = row.createCell(colStatus);
                    Object statusOfLoan = getMetadataValue(ssrs, "statusOfLoan");
                    statusCell.setCellValue(statusOfLoan != null ? statusOfLoan.toString() : "");
                    statusCell.setCellStyle(centerDataStyle);
                    
                    // BS OPENING Principle receivable 90% (blue, right-aligned numbers)
                    Cell bsFtmBeginningPRCell = row.createCell(colBSFtmBeginningPR90);
                    Object bsFtmBeginningPRValue = getMetadataValue(ssrs, "bsftmBeginningPrincipleReceivable90");
                    setNumericCellValue(bsFtmBeginningPRCell, bsFtmBeginningPRValue);
                    bsFtmBeginningPRCell.setCellStyle(blueDataStyle);
                    
                    // BS ITD End Principle receivable 90% (blue, right-aligned numbers)
                    Cell bsItdCell = row.createCell(colBSITDEnd);
                    Object bsItdValue = getMetadataValue(ssrs, "bsItdEndPrincipleReceivable90");
                    setNumericCellValue(bsItdCell, bsItdValue);
                    bsItdCell.setCellStyle(blueDataStyle);
                    
                    // Principal DA (sandal, right-aligned numbers)
                    Cell principalDACell = row.createCell(colPrincipalDA);
                    Object principalDAValue = getMetadataValue(ssrs, "bsFtmLoanBalance90");
                    setNumericCellValue(principalDACell, principalDAValue);
                    principalDACell.setCellStyle(sandalDataStyle);
                    
                    // VD PR (sandal, right-aligned numbers)
                    Cell vdprCell = row.createCell(colVDPR);
                    Object vdprValue = getMetadataValue(ssrs, "bsFtmPrincipleReceivable90");
                    setNumericCellValue(vdprCell, vdprValue);
                    vdprCell.setCellStyle(sandalDataStyle);

                    // PL FTM Bad Debt 90 (sandal, right-aligned numbers)
                    Cell plFtmDebt90Cell = row.createCell(colPLFtmDebt90);
                    Object plFtmDebt90Value = getMetadataValue(ssrs, "plFtmDebt90");
                    setNumericCellValue(plFtmDebt90Cell, plFtmDebt90Value);
                    plFtmDebt90Cell.setCellStyle(sandalDataStyle);

                    // PL FTM Bad Debt Recovery 90 (sandal, right-aligned numbers)
                    Cell plFtmBadDebtRecovery90Cell = row.createCell(colPLFtmBadDebtRecovery90);
                    Object plFtmBadDebtRecovery90Value = getMetadataValue(ssrs, "plFtmBadDebtRecovery90");
                    setNumericCellValue(plFtmBadDebtRecovery90Cell, plFtmBadDebtRecovery90Value);
                    plFtmBadDebtRecovery90Cell.setCellStyle(sandalDataStyle);

                    // PL FTM Settlement Loss 90 (sandal, right-aligned numbers)
                    Cell plFtmSettlementLoss90Cell = row.createCell(colPLFtmSettlementLoss90);
                    Object plFtmSettlementLoss90Value = getMetadataValue(ssrs, "plFtmSettlementLoss90");
                    setNumericCellValue(plFtmSettlementLoss90Cell, plFtmSettlementLoss90Value);
                    plFtmSettlementLoss90Cell.setCellStyle(sandalDataStyle);
                    
                    // Total VD (sandal, right-aligned, formula)
                    Cell totalVDCell = row.createCell(colTotalVD);
                    String principalDACol = getColumnLetter(colPrincipalDA);
                    String vdprCol = getColumnLetter(colVDPR);
                    String plFtmDebt90Col = getColumnLetter(colPLFtmDebt90);
                    String plFtmBadDebtRecovery90Col = getColumnLetter(colPLFtmBadDebtRecovery90);
                    String plFtmSettlementLoss90Col = getColumnLetter(colPLFtmSettlementLoss90);
                    totalVDCell.setCellFormula(String.format("%s%d+%s%d+%s%d+%s%d+%s%d",
                            principalDACol, rowIdx + 1,
                            vdprCol, rowIdx + 1,
                            plFtmDebt90Col, rowIdx + 1,
                            plFtmBadDebtRecovery90Col, rowIdx + 1,
                            plFtmSettlementLoss90Col, rowIdx + 1));
                    totalVDCell.setCellStyle(sandalDataStyle);
                    
                    // Payout Report (blue, right-aligned numbers)
                    Cell payoutReportCell = row.createCell(colPayoutReport);
                    if (payout != null) {
                        BigDecimal payoutReport = safeSubtract(
                            payout.getSellerTotalPrincipalComponentPaid(),
                            payout.getSellerPrincipalOverduePaid()
                        );
                        setNumericCellValue(payoutReportCell, payoutReport);
                    } else {
                        payoutReportCell.setCellValue(0);
                    }
                    payoutReportCell.setCellStyle(blueDataStyle);
                    
                    // Overdue PR (blue, right-aligned numbers)
                    Cell overduePRCell = row.createCell(colOverduePR);
                    if (payout != null && payout.getSellerPrincipalOverduePaid() != null) {
                        setNumericCellValue(overduePRCell, payout.getSellerPrincipalOverduePaid());
                    } else {
                        overduePRCell.setCellValue(0);
                    }
                    overduePRCell.setCellStyle(blueDataStyle);
                    
                    // Part Payment/FC (blue, right-aligned numbers)
                    Cell partPaymentFCCell = row.createCell(colPartPaymentFC);
                    if (payout != null) {
                        BigDecimal partPaymentFC = safeAdd(
                            payout.getSellerPrepaymentPaid(),
                            payout.getSellerForeclosurePaid()
                        );
                        setNumericCellValue(partPaymentFCCell, partPaymentFC);
                    } else {
                        partPaymentFCCell.setCellValue(0);
                    }
                    partPaymentFCCell.setCellStyle(blueDataStyle);
                    
                    // Total payout (blue, right-aligned, formula)
                    Cell totalPayoutCell = row.createCell(colTotalPayout);
                    String payoutReportCol = getColumnLetter(colPayoutReport);
                    String overduePRCol = getColumnLetter(colOverduePR);
                    String partPaymentFCCol = getColumnLetter(colPartPaymentFC);
                    totalPayoutCell.setCellFormula(String.format("%s%d+%s%d+%s%d", 
                        payoutReportCol, rowIdx + 1, overduePRCol, rowIdx + 1, partPaymentFCCol, rowIdx + 1));
                    totalPayoutCell.setCellStyle(blueDataStyle);
                    
                    // Diff (sandal, right-aligned, formula)
                    Cell diffCell = row.createCell(colDiff);
                    String totalVDCol = getColumnLetter(colTotalVD);
                    String totalPayoutCol = getColumnLetter(colTotalPayout);
                    diffCell.setCellFormula(String.format("%s%d+%s%d", totalVDCol, rowIdx + 1, totalPayoutCol, rowIdx + 1));
                    diffCell.setCellStyle(sandalDataStyle);
                    
                    // Overdue check (sandal, right-aligned, formula with conditional formatting for negative)
                    Cell overdueCheckCell = row.createCell(colOverdueCheck);
                    String bsFtmBeginningPRCol = getColumnLetter(colBSFtmBeginningPR90);
                    String overduePRColForCheck = getColumnLetter(colOverduePR);
                    // Formula: BS OPENING Principle receivable 90% - Overdue PR
                    overdueCheckCell.setCellFormula(String.format("%s%d-%s%d", bsFtmBeginningPRCol, rowIdx + 1, overduePRColForCheck, rowIdx + 1));
                    // Note: Conditional formatting for negative values would need to be applied via Excel's conditional formatting feature
                    // For now, we'll use sandal style, but the formula will show negative values
                    overdueCheckCell.setCellStyle(sandalDataStyle);

                    // Principal Remarks (centered text, based on Diff)
                    Cell principalRemarksCell = row.createCell(colPrincipalRemarks);
                    String diffCol = getColumnLetter(colDiff);
                    principalRemarksCell.setCellFormula(String.format("IF(ABS(%s%d)<=1,\"Ok\",\"Not Ok\")", diffCol, rowIdx + 1));
                    principalRemarksCell.setCellStyle(centerDataStyle);
                    
                    // Empty columns
                    Cell empty1Cell = row.createCell(colEmpty1);
                    empty1Cell.setCellValue("");
                    empty1Cell.setCellStyle(sandalDataStyle);
                    
                    Cell empty2Cell = row.createCell(colEmpty2);
                    empty2Cell.setCellValue("");
                    empty2Cell.setCellStyle(sandalDataStyle);
                    
                    // First set: Bounce Charges columns
                    // Bounce charges DA (sandal, right-aligned)
                    Cell plFtmInstructBounceCharges90Cell = row.createCell(colPlFtmInstructBounceCharges90);
                    Object plFtmInstructBounceCharges90Value = getMetadataValue(ssrs, "plFtmInstructBounceCharges90");
                    setNumericCellValue(plFtmInstructBounceCharges90Cell, plFtmInstructBounceCharges90Value);
                    plFtmInstructBounceCharges90Cell.setCellStyle(sandalDataStyle);
                    
                    // Payout Bounce Charges (blue, right-aligned) - sellerTotalChargesPaid - sellerForeclosureChargesPaid - sellerPrepaymentPaid
                    Cell payoutBounceChargesCell = row.createCell(colPayoutBounceCharges);
                    if (payout != null) {
                        BigDecimal payoutBounceCharges = safeSubtract(
                            safeSubtract(
                                payout.getSellerTotalChargesPaid(),
                                payout.getSellerForeclosureChargesPaid()
                            ),
                            payout.getSellerPrepaymentChargesPaid()
                        );
                        setNumericCellValue(payoutBounceChargesCell, payoutBounceCharges);
                    } else {
                        payoutBounceChargesCell.setCellValue(0);
                    }
                    payoutBounceChargesCell.setCellStyle(blueDataStyle);
                    
                    // Diff bounce charges (sandal, right-aligned, formula)
                    Cell diffBounceChargesCell = row.createCell(colDiffBounceCharges);
                    String plFtmInstructBounceCharges90Col = getColumnLetter(colPlFtmInstructBounceCharges90);
                    String payoutBounceChargesCol = getColumnLetter(colPayoutBounceCharges);
                    diffBounceChargesCell.setCellFormula(String.format("%s%d-%s%d", 
                        plFtmInstructBounceCharges90Col, rowIdx + 1, payoutBounceChargesCol, rowIdx + 1));
                    diffBounceChargesCell.setCellStyle(sandalDataStyle);
                    
                    // Remarks bounce charges (centered text, based on Diff)
                    Cell remarksBounceChargesCell = row.createCell(colRemarksBounceCharges);
                    String diffBounceChargesCol = getColumnLetter(colDiffBounceCharges);
                    remarksBounceChargesCell.setCellFormula(String.format("IF(ABS(%s%d)<=1,\"Ok\",\"Not Ok\")", diffBounceChargesCol, rowIdx + 1));
                    remarksBounceChargesCell.setCellStyle(centerDataStyle);
                    
                    // Second set: Foreclosure Charges columns
                    // Foreclosure charges DA (sandal, right-aligned)
                    Cell plFtmForeclosureCharges90Cell = row.createCell(colPlFtmForeclosureCharges90);
                    Object plFtmForeclosureCharges90Value = getMetadataValue(ssrs, "plFtmForeclosureCharges90");
                    setNumericCellValue(plFtmForeclosureCharges90Cell, plFtmForeclosureCharges90Value);
                    plFtmForeclosureCharges90Cell.setCellStyle(sandalDataStyle);
                    
                    // Payout foreclosure charges (blue, right-aligned) - sellerForeclosureChargesPaid
                    // If sellerClosingPos == 0, add sellerPrepaymentPaid, otherwise just sellerForeclosureChargesPaid
                    Cell payoutForeclosureChargesCell = row.createCell(colPayoutForeclosureCharges);
                    if (payout != null && payout.getSellerForeclosureChargesPaid() != null) {
                        BigDecimal foreclosureCharges = payout.getSellerForeclosureChargesPaid();
                        // Only add sellerPrepaymentPaid if sellerClosingPos == 0
                        if (payout.getSellerClosingPos() != null && payout.getSellerClosingPos().compareTo(BigDecimal.ZERO) == 0) {
                            foreclosureCharges = safeAdd(foreclosureCharges, payout.getSellerPrepaymentPaid());
                        }
                        setNumericCellValue(payoutForeclosureChargesCell, foreclosureCharges);
                    } else {
                        payoutForeclosureChargesCell.setCellValue(0);
                    }
                    payoutForeclosureChargesCell.setCellStyle(blueDataStyle);
                    
                    // Diff Foreclosure charges (sandal, right-aligned, formula)
                    Cell diffForeclosureChargesCell = row.createCell(colDiffForeclosureCharges);
                    String plFtmForeclosureCharges90Col = getColumnLetter(colPlFtmForeclosureCharges90);
                    String payoutForeclosureChargesCol = getColumnLetter(colPayoutForeclosureCharges);
                    diffForeclosureChargesCell.setCellFormula(String.format("%s%d-%s%d", 
                        plFtmForeclosureCharges90Col, rowIdx + 1, payoutForeclosureChargesCol, rowIdx + 1));
                    diffForeclosureChargesCell.setCellStyle(sandalDataStyle);
                    
                    // Remarks Foreclosure charges (centered text, based on Diff)
                    Cell remarksForeclosureChargesCell = row.createCell(colRemarksForeclosureCharges);
                    String diffForeclosureChargesCol = getColumnLetter(colDiffForeclosureCharges);
                    remarksForeclosureChargesCell.setCellFormula(String.format("IF(ABS(%s%d)<=1,\"Ok\",\"Not Ok\")", diffForeclosureChargesCol, rowIdx + 1));
                    remarksForeclosureChargesCell.setCellStyle(centerDataStyle);
                    
                    rowIdx++;
                }

                // Set column widths to be more spacious
                sheet.setColumnWidth(colLAN, 4500); // More spacious for LAN
                sheet.setColumnWidth(colStatus, 5500); // More spacious for Status
                sheet.setColumnWidth(colBSFtmBeginningPR90, 7000); // Wider for long header
                sheet.setColumnWidth(colBSITDEnd, 7000); // Much wider for long header
                sheet.setColumnWidth(colPrincipalDA, 5500); // More spacious
                sheet.setColumnWidth(colVDPR, 5000); // More spacious
                sheet.setColumnWidth(colPLFtmDebt90, 6000); // More spacious
                sheet.setColumnWidth(colPLFtmBadDebtRecovery90, 6500); // More spacious
                sheet.setColumnWidth(colPLFtmSettlementLoss90, 6500); // More spacious
                sheet.setColumnWidth(colTotalVD, 5500); // More spacious
                sheet.setColumnWidth(colPayoutReport, 6000); // More spacious
                sheet.setColumnWidth(colOverduePR, 5500); // More spacious
                sheet.setColumnWidth(colPartPaymentFC, 6000); // More spacious
                sheet.setColumnWidth(colTotalPayout, 5500); // More spacious
                sheet.setColumnWidth(colDiff, 5000); // More spacious for Diff
                sheet.setColumnWidth(colOverdueCheck, 6000); // More spacious for Overdue check
                sheet.setColumnWidth(colPrincipalRemarks, 6000); // More spacious for Principal Remarks
                // Empty columns
                sheet.setColumnWidth(colEmpty1, 3000);
                sheet.setColumnWidth(colEmpty2, 3000);
                // Bounce Charges columns
                sheet.setColumnWidth(colPlFtmInstructBounceCharges90, 6000);
                sheet.setColumnWidth(colPayoutBounceCharges, 6000);
                sheet.setColumnWidth(colDiffBounceCharges, 5000);
                sheet.setColumnWidth(colRemarksBounceCharges, 5000);
                // Foreclosure Charges columns
                sheet.setColumnWidth(colPlFtmForeclosureCharges90, 6500);
                sheet.setColumnWidth(colPayoutForeclosureCharges, 6000);
                sheet.setColumnWidth(colDiffForeclosureCharges, 5000);
                sheet.setColumnWidth(colRemarksForeclosureCharges, 5000);

                // Sheet 2: Interest Validations
                createInterestValidationsSheet(workbook, ssrsData, payoutMap, deal, year, month);

                workbook.write(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Failed to build Excel file", e);
            }
        });
    }

    private void setSumFormula(Row row, int colIndex, int dataStartRow, int dataEndRow) {
        String columnLetter = getColumnLetter(colIndex);
        String formula = String.format("SUM(%s%d:%s%d)", columnLetter, dataStartRow + 1, columnLetter, dataEndRow + 1);
        row.createCell(colIndex).setCellFormula(formula);
    }

    private XSSFCellStyle createWhiteDataCellStyle(XSSFWorkbook workbook, 
                                                    org.apache.poi.ss.usermodel.HorizontalAlignment alignment) {
        XSSFCellStyle style = workbook.createCellStyle();
        // White background (default, no fill needed)
        style.setAlignment(alignment);
        style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        // Add padding for more spacious cells
        style.setIndention((short) 1); // Small indentation
        return style;
    }

    private Object getMetadataValue(SSRSFileDataEntity ssrs, String key) {
        if (ssrs.getMetadata() != null && ssrs.getMetadata().containsKey(key)) {
            return ssrs.getMetadata().get(key);
        }
        return null;
    }

    private void setNumericCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue(0);
            return;
        }
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else {
            try {
                cell.setCellValue(Double.parseDouble(value.toString()));
            } catch (NumberFormatException e) {
                cell.setCellValue(0);
            }
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

    private void createInterestValidationsSheet(XSSFWorkbook workbook, List<SSRSFileDataEntity> ssrsData, 
                                                Map<String, PartnerPayoutDetailsAll> payoutMap, Deal deal, 
                                                Integer year, Integer month) {
        Sheet sheet = workbook.createSheet("Interest Validations");

        // Create color styles (reuse colors from Sheet1)
        XSSFFont boldFont = workbook.createFont();
        boldFont.setBold(true);
        boldFont.setColor(IndexedColors.WHITE.getIndex());
        
        // Green color
        org.apache.poi.xssf.usermodel.XSSFColor greenColor = new org.apache.poi.xssf.usermodel.XSSFColor(
            new byte[]{(byte)146, (byte)208, (byte)80}, null);
        
        // Sandal color
        org.apache.poi.xssf.usermodel.XSSFColor sandalColor = new org.apache.poi.xssf.usermodel.XSSFColor(
            new byte[]{(byte)230, (byte)184, (byte)183}, null);
        
        // Light blue color
        org.apache.poi.xssf.usermodel.XSSFColor lightBlueColor = new org.apache.poi.xssf.usermodel.XSSFColor(
            new byte[]{(byte)173, (byte)216, (byte)230}, null);
        
        // Gray color
        org.apache.poi.xssf.usermodel.XSSFColor grayColor = new org.apache.poi.xssf.usermodel.XSSFColor(
            new byte[]{(byte)217, (byte)217, (byte)217}, null);

        // Header styles
        XSSFCellStyle greenHeaderStyle = createHeaderStyle(workbook, greenColor, boldFont);
        XSSFCellStyle sandalHeaderStyle = createHeaderStyle(workbook, sandalColor, boldFont);
        XSSFCellStyle lightBlueHeaderStyle = createHeaderStyle(workbook, lightBlueColor, boldFont);
        XSSFCellStyle grayHeaderStyle = createHeaderStyle(workbook, grayColor, boldFont);

        // Data styles (white background)
        XSSFCellStyle greenDataStyle = createWhiteDataCellStyle(workbook, 
            org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);
        XSSFCellStyle sandalDataStyle = createWhiteDataCellStyle(workbook, 
            org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
        XSSFCellStyle lightBlueDataStyle = createWhiteDataCellStyle(workbook, 
            org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
        XSSFCellStyle grayDataStyle = createWhiteDataCellStyle(workbook, 
            org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);

        // Column indices
        int colLAN = 0;
        int colOpeningFuturePrincipal = 1;
        int colAF = 2;
        int colDiff = 3;
        int colOpeningInterestOverdue = 4;
        int colClosingOverdue = 5;
        int colCutOffDate = 6;
        int colNoOfDays = 7;
        int colFTPInterestDA = 8;
        int colPayoutReport = 9;
        int colOverdueInterestCollection = 10;
        int colTotalPayout = 11;
        int colDiff1 = 12;

        // Get deal rate (stored as decimal, e.g., 0.23 for 23%)
        Double dealRate = (deal != null && deal.getAnnualInterestRate() != null) 
            ? deal.getAnnualInterestRate()  // Use as decimal (0.23 for 23%)
            : 0.0;

        // SUM row (row 0)
        Row sumRow = sheet.createRow(0);
        int dataStartRow = 2;
        int dataEndRow = dataStartRow + ssrsData.size() - 1;
        
        sumRow.createCell(colLAN).setCellValue("SUM");
        setSumFormula(sumRow, colOpeningFuturePrincipal, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colAF, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colDiff, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colOpeningInterestOverdue, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colClosingOverdue, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colNoOfDays, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colFTPInterestDA, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colPayoutReport, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colOverdueInterestCollection, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colTotalPayout, dataStartRow, dataEndRow);
        setSumFormula(sumRow, colDiff1, dataStartRow, dataEndRow);

        // Header row (row 1)
        Row header = sheet.createRow(1);
        header.setHeightInPoints(35);
        
        header.createCell(colLAN).setCellValue("LAN");
        header.getCell(colLAN).setCellStyle(greenHeaderStyle);
        
        header.createCell(colOpeningFuturePrincipal).setCellValue("Opening Future Principal");
        header.getCell(colOpeningFuturePrincipal).setCellStyle(greenHeaderStyle);
        
        header.createCell(colAF).setCellValue("AF");
        header.getCell(colAF).setCellStyle(sandalHeaderStyle);
        
        header.createCell(colDiff).setCellValue("Diff");
        header.getCell(colDiff).setCellStyle(sandalHeaderStyle);
        
        header.createCell(colOpeningInterestOverdue).setCellValue("Opening interest Overdue");
        header.getCell(colOpeningInterestOverdue).setCellStyle(grayHeaderStyle);
        
        header.createCell(colClosingOverdue).setCellValue("Closing Overdue");
        header.getCell(colClosingOverdue).setCellStyle(grayHeaderStyle);
        
        header.createCell(colCutOffDate).setCellValue("Cut-Off Date");
        header.getCell(colCutOffDate).setCellStyle(grayHeaderStyle);
        
        header.createCell(colNoOfDays).setCellValue("No of days");
        header.getCell(colNoOfDays).setCellStyle(grayHeaderStyle);
        
        header.createCell(colFTPInterestDA).setCellValue("FTP -Interest DA");
        header.getCell(colFTPInterestDA).setCellStyle(sandalHeaderStyle);
        
        header.createCell(colPayoutReport).setCellValue("Payout Report");
        header.getCell(colPayoutReport).setCellStyle(lightBlueHeaderStyle);
        
        header.createCell(colOverdueInterestCollection).setCellValue("Overdue Interest collection");
        header.getCell(colOverdueInterestCollection).setCellStyle(lightBlueHeaderStyle);
        
        header.createCell(colTotalPayout).setCellValue("Total Payout");
        header.getCell(colTotalPayout).setCellStyle(lightBlueHeaderStyle);
        
        header.createCell(colDiff1).setCellValue("Diff 1");
        header.getCell(colDiff1).setCellStyle(grayHeaderStyle);

        // Data rows (starting from row 2)
        int rowIdx = dataStartRow;
        for (SSRSFileDataEntity ssrs : ssrsData) {
            Row row = sheet.createRow(rowIdx);
            row.setHeightInPoints(22);
            PartnerPayoutDetailsAll payout = payoutMap.get(ssrs.getLmsLan());
            
            // LAN (green, left-aligned)
            Cell lanCell = row.createCell(colLAN);
            lanCell.setCellValue(ssrs.getLmsLan() != null ? ssrs.getLmsLan() : "");
            lanCell.setCellStyle(greenDataStyle);
            
            // Opening Future Principal (green, right-aligned) - sellerOpeningPos - sellerPrincipalOverdue
            Cell openingFuturePrincipalCell = row.createCell(colOpeningFuturePrincipal);
            if (payout != null) {
                BigDecimal openingFuturePrincipal = safeSubtract(
                    payout.getSellerOpeningPos(),
                    payout.getSellerPrincipalOverdue()
                );
                setNumericCellValue(openingFuturePrincipalCell, openingFuturePrincipal);
            } else {
                openingFuturePrincipalCell.setCellValue(0);
            }
            openingFuturePrincipalCell.setCellStyle(greenDataStyle);
            
            // AF (sandal, right-aligned) - bsItdBeginningLoanBalance90
            Cell afCell = row.createCell(colAF);
            Object afValue = getMetadataValue(ssrs, "bsItdBeginningLoanBalance90");
            setNumericCellValue(afCell, afValue);
            afCell.setCellStyle(sandalDataStyle);
            
            // Diff (sandal, right-aligned, formula) - Opening Future Principal - AF
            Cell diffCell = row.createCell(colDiff);
            String openingFuturePrincipalCol = getColumnLetter(colOpeningFuturePrincipal);
            String afCol = getColumnLetter(colAF);
            diffCell.setCellFormula(String.format("%s%d-%s%d", openingFuturePrincipalCol, rowIdx + 1, afCol, rowIdx + 1));
            diffCell.setCellStyle(sandalDataStyle);
            
            // Opening interest Overdue (gray, right-aligned) - sellerInterestOverdue
            Cell openingInterestOverdueCell = row.createCell(colOpeningInterestOverdue);
            if (payout != null && payout.getSellerInterestOverdue() != null) {
                setNumericCellValue(openingInterestOverdueCell, payout.getSellerInterestOverdue());
            } else {
                openingInterestOverdueCell.setCellValue(0);
            }
            openingInterestOverdueCell.setCellStyle(grayDataStyle);
            
            // Closing Overdue (gray, right-aligned, formula) - sellerTotalInterestComponentPaid - sellerTotalInterestDue
            Cell closingOverdueCell = row.createCell(colClosingOverdue);
            if (payout != null) {
                BigDecimal closingOverdue = safeSubtract(
                    payout.getSellerTotalInterestComponentPaid(),
                    payout.getSellerTotalInterestDue()
                );
                setNumericCellValue(closingOverdueCell, closingOverdue);
            } else {
                closingOverdueCell.setCellValue(0);
            }
            closingOverdueCell.setCellStyle(grayDataStyle);
            
            // Cut-Off Date (gray, right-aligned) - cycleEndDate
            Cell cutOffDateCell = row.createCell(colCutOffDate);
            if (payout != null && payout.getCycleEndDate() != null) {
                cutOffDateCell.setCellValue(payout.getCycleEndDate().toString());
            } else {
                cutOffDateCell.setCellValue("");
            }
            cutOffDateCell.setCellStyle(grayDataStyle);
            
            // No of days (gray, right-aligned) - calculate days in month from cycleEndDate
            Cell noOfDaysCell = row.createCell(colNoOfDays);
            if (payout != null && payout.getCycleEndDate() != null) {
                int daysInMonth = payout.getCycleEndDate().lengthOfMonth();
                noOfDaysCell.setCellValue(daysInMonth);
            } else {
                noOfDaysCell.setCellValue(0);
            }
            noOfDaysCell.setCellStyle(grayDataStyle);
            
            // FTP - Interest DA (sandal, right-aligned, formula) - ((Opening Future Principal * Deal rate)/365 * No of days)
            Cell ftpInterestDACell = row.createCell(colFTPInterestDA);
            String openingFuturePrincipalColForFTP = getColumnLetter(colOpeningFuturePrincipal);
            String noOfDaysCol = getColumnLetter(colNoOfDays);
            // Formula: ((Opening Future Principal * Deal rate)/365 * No of days)
            // Deal rate is stored as decimal (e.g., 0.23 for 23%)
            String ftpFormula = String.format("((%s%d*%.6f)/365*%s%d)", 
                openingFuturePrincipalColForFTP, rowIdx + 1, dealRate, noOfDaysCol, rowIdx + 1);
            ftpInterestDACell.setCellFormula(ftpFormula);
            ftpInterestDACell.setCellStyle(sandalDataStyle);
            
            // Payout Report (light blue, right-aligned) - sellerTotalInterestComponentPaid - sellerInterestOverduePaid
            Cell payoutReportCell = row.createCell(colPayoutReport);
            if (payout != null) {
                BigDecimal payoutReport = safeSubtract(
                    payout.getSellerTotalInterestComponentPaid(),
                    payout.getSellerInterestOverduePaid()
                );
                setNumericCellValue(payoutReportCell, payoutReport);
            } else {
                payoutReportCell.setCellValue(0);
            }
            payoutReportCell.setCellStyle(lightBlueDataStyle);
            
            // Overdue Interest collection (light blue, right-aligned) - sellerInterestOverduePaid
            Cell overdueInterestCollectionCell = row.createCell(colOverdueInterestCollection);
            if (payout != null && payout.getSellerInterestOverduePaid() != null) {
                setNumericCellValue(overdueInterestCollectionCell, payout.getSellerInterestOverduePaid());
            } else {
                overdueInterestCollectionCell.setCellValue(0);
            }
            overdueInterestCollectionCell.setCellStyle(lightBlueDataStyle);
            
            // Total Payout (light blue, right-aligned, formula) - Payout Report + Overdue Interest collection
            Cell totalPayoutCell = row.createCell(colTotalPayout);
            String payoutReportCol = getColumnLetter(colPayoutReport);
            String overdueInterestCollectionCol = getColumnLetter(colOverdueInterestCollection);
            totalPayoutCell.setCellFormula(String.format("%s%d+%s%d", 
                payoutReportCol, rowIdx + 1, overdueInterestCollectionCol, rowIdx + 1));
            totalPayoutCell.setCellStyle(lightBlueDataStyle);
            
            // Diff 1 (gray, right-aligned, formula) - FTP - Interest DA - Total Payout
            Cell diff1Cell = row.createCell(colDiff1);
            String ftpInterestDACol = getColumnLetter(colFTPInterestDA);
            String totalPayoutCol = getColumnLetter(colTotalPayout);
            diff1Cell.setCellFormula(String.format("%s%d-%s%d", ftpInterestDACol, rowIdx + 1, totalPayoutCol, rowIdx + 1));
            diff1Cell.setCellStyle(grayDataStyle);
            
            rowIdx++;
        }

        // Set column widths to be more spacious
        sheet.setColumnWidth(colLAN, 4500);
        sheet.setColumnWidth(colOpeningFuturePrincipal, 6000);
        sheet.setColumnWidth(colAF, 5000);
        sheet.setColumnWidth(colDiff, 5000);
        sheet.setColumnWidth(colOpeningInterestOverdue, 6000);
        sheet.setColumnWidth(colClosingOverdue, 5500);
        sheet.setColumnWidth(colCutOffDate, 5000);
        sheet.setColumnWidth(colNoOfDays, 4500);
        sheet.setColumnWidth(colFTPInterestDA, 5500);
        sheet.setColumnWidth(colPayoutReport, 6000);
        sheet.setColumnWidth(colOverdueInterestCollection, 6500);
        sheet.setColumnWidth(colTotalPayout, 5500);
        sheet.setColumnWidth(colDiff1, 5000);
    }

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook workbook, org.apache.poi.xssf.usermodel.XSSFColor color, XSSFFont font) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setFont(font);
        style.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setIndention((short) 1);
        return style;
    }
}
