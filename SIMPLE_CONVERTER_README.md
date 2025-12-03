# Simple Excel Converter - Standalone Java Class

A simple standalone Java program to convert horizontal Excel data to vertical format.

## What it does

Converts Excel data from this format:
```
LAN                    | Sep-25 | Oct-25 | Nov-25 | Dec-25
MFLAPDSECUL000005078783 | 6795.0 | 6920.0 | 7047.0 | 7176.0
```

To this format:
```
LAN                    | Month  | Value
MFLAPDSECUL000005078783 | Sep-25 | 6795.0
MFLAPDSECUL000005078783 | Oct-25 | 6920.0
MFLAPDSECUL000005078783 | Nov-25 | 7047.0
MFLAPDSECUL000005078783 | Dec-25 | 7176.0
```

## How to use

### Compile the class
```bash
javac -cp "target/classes:$(find ~/.m2/repository -name "*.jar" | tr '\n' ':')" src/main/java/com/finvolv/selldown/ExcelConverter.java
```

### Run the program
```bash
java -cp "src/main/java:target/classes:$(find ~/.m2/repository -name "*.jar" | tr '\n' ':')" com.finvolv.selldown.ExcelConverter "Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx" "LAN wise cashflows" "LAN"
```

### Parameters
1. **filePath**: Path to your Excel file
2. **sheetName**: Name of the sheet to process
3. **lanColumnName**: Name of the LAN column (optional, defaults to "LAN")

## Examples

```bash
# Basic usage
java com.finvolv.selldown.ExcelConverter "Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx" "LAN wise cashflows"

# With custom LAN column name
java com.finvolv.selldown.ExcelConverter "Sept Deal'25/Muthoot DA - Sept'25/LAN wise cashflows/LAN wise cashflows.xlsx" "LAN wise cashflows" "Loan Account Number"
```

## Output

The converted file will be saved to:
`src/main/resources/excel-outputs/{original_filename}_converted_{timestamp}.xlsx`

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
