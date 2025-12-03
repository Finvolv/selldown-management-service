# POS Calculator - Opening POS and Closing POS Calculator

A standalone Java program that calculates Opening POS and Closing POS for each LAN in Excel data.

## What it does

Takes horizontal Excel data and adds Opening POS and Closing POS calculations:

### **Input Format (Horizontal):**
```
LAN                    | Sep-25 | Oct-25 | Nov-25 | Dec-25
MFLAPDSECUL000005078783 | 6795.0 | 6920.0 | 7047.0 | 7176.0
```

### **Output Format (Vertical with POS):**
```
LAN                    | Month      | Principal | Opening POS | Closing POS
MFLAPDSECUL000005078783 | 30-SEP-2025| 6795.0    | 772584.48   | 765789.48
MFLAPDSECUL000005078783 | 31-OCT-2025| 6920.0    | 765789.48   | 758869.48
MFLAPDSECUL000005078783 | 30-NOV-2025| 7047.0    | 758869.48   | 751822.48
MFLAPDSECUL000005078783 | 31-DEC-2025| 7176.0    | 751822.48   | 744646.48
```

## POS Calculation Logic

### **Opening POS:**
- **First Month**: Sum of all principal values for that LAN
- **Subsequent Months**: Previous month's Closing POS

### **Closing POS:**
- **Formula**: Opening POS - Principal
- **Next Month Opening POS**: Current month's Closing POS

## How to use

### **Compile:**
```bash
javac -cp "target/classes:$(find ~/.m2/repository -name "*.jar" | tr '\n' ':')" src/main/java/com/finvolv/selldown/PosCalculator.java
```

### **Run:**
```bash
mvn exec:java -Dexec.mainClass="com.finvolv.selldown.PosCalculator" -Dexec.args="\"Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx\" \"I\" \"LAN\""
```

### **Parameters:**
1. **filePath**: Path to your Excel file
2. **sheetName**: Name of the sheet to process (e.g., "I")
3. **lanColumnName**: Name of the LAN column (optional, defaults to "LAN")

## Examples

```bash
# Basic usage
mvn exec:java -Dexec.mainClass="com.finvolv.selldown.PosCalculator" -Dexec.args="\"Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx\" \"I\""

# With custom LAN column name
mvn exec:java -Dexec.mainClass="com.finvolv.selldown.PosCalculator" -Dexec.args="\"Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx\" \"I\" \"Loan Account Number\""
```

## Output

The calculated file will be saved to:
`src/main/resources/excel-outputs/{original_filename}_pos_calculated_{timestamp}.xlsx`

## Features

- ✅ **Opening POS Calculation**: Sum of all principal values for each LAN
- ✅ **Closing POS Calculation**: Opening POS - Principal for each month
- ✅ **Month-to-Month POS**: Next month Opening POS = Previous month Closing POS
- ✅ **Zero Value Filtering**: Skips rows where Principal = 0
- ✅ **Month Formatting**: DD-MMM-YYYY format (e.g., 30-SEP-2025)
- ✅ **Multiple LAN Support**: Handles multiple LANs in the same file

## Requirements

- Java 17+
- Apache POI libraries (already included in your project)
- Excel file with horizontal data structure

## Error Handling

The program will show helpful error messages if:
- File doesn't exist
- Sheet name is not found
- LAN column is not found
- Any other processing errors occur
