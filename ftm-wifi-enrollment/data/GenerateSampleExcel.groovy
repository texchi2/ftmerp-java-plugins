#!/usr/bin/env groovy
// GenerateSampleExcel.groovy
// Standalone script — run on rpitex or ltsp to generate sample_ftm_staff_import.xlsx
// Usage: groovy GenerateSampleExcel.groovy
//
// Requires Apache POI on classpath. If running outside OFBiz:
//   Download poi-5.x.jar + poi-ooxml-5.x.jar + commons-io.jar
//   groovy -cp "poi-5.x.jar:poi-ooxml-5.x.jar:commons-io.jar" GenerateSampleExcel.groovy
//
// Inside OFBiz environment, run via:
//   ./gradlew "ofbiz --test component=ftm-wifi-enrollment,case=GenerateSampleExcel"

@Grab(group='org.apache.poi', module='poi-ooxml', version='5.2.3')
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.ss.usermodel.*

def workbook = new XSSFWorkbook()
def sheet = workbook.createSheet("FTM Staff Import")

// Header style
def headerStyle = workbook.createCellStyle()
def headerFont = workbook.createFont()
headerFont.setBold(true)
headerStyle.setFont(headerFont)
headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex())
headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)

// Headers — must match ImportUsersFromExcel.groovy column mapping exactly
def headers = [
    "Employee ID",
    "Full Name",
    "Username",
    "Department",
    "Position",
    "Device Quota",
    "FTM-Staff VLAN10 (Y/N)",
    "Notes"
]

def headerRow = sheet.createRow(0)
headers.eachWithIndex { header, i ->
    def cell = headerRow.createCell(i)
    cell.setCellValue(header)
    cell.setCellStyle(headerStyle)
    sheet.autoSizeColumn(i)
}

// Sample data rows — using existing FTM staff from authorized_users
def sampleData = [
    ["FTM001", "Fan CEO",         "fan.ceo",     "Management", "Chief Executive Officer",       2, "Y", ""],
    ["FTM002", "Jamal Tex",       "jamal.tex",   "IT",         "IT Head / Project Manager",     4, "Y", "Phase 6 test account"],
    ["FTM003", "Chang Shu-Ling",  "chang.shuling","Management","Operations Manager",            2, "Y", ""],
    ["FTM004", "John Doe",        "john.doe",    "Production", "Production Supervisor",         2, "N", ""],
    ["FTM005", "Jane Smith",      "jane.smith",  "HR",         "HR Manager",                    2, "Y", ""],
    ["FTM006", "Wei Ming",        "wei.ming",    "Finance",    "Finance Officer",               2, "N", ""],
    ["FTM011", "New Hire",        "new.hire",    "Production", "Operator",                      1, "N", "New staff import example"],
]

sampleData.eachWithIndex { row, rowIdx ->
    def dataRow = sheet.createRow(rowIdx + 1)
    row.eachWithIndex { value, colIdx ->
        def cell = dataRow.createCell(colIdx)
        if (value instanceof Integer) {
            cell.setCellValue((double) value)
        } else {
            cell.setCellValue(value.toString())
        }
    }
}

// Auto-size all columns
(0..7).each { sheet.autoSizeColumn(it) }

// Write to file
def outputPath = "sample_ftm_staff_import.xlsx"
def fos = new FileOutputStream(outputPath)
workbook.write(fos)
fos.close()
workbook.close()

println "Generated: ${outputPath}"
println "Rows: ${sampleData.size()} staff + 1 header"
println "Copy to rpitex: scp ${outputPath} texchi@192.168.30.129:/home/texchi/ofbiz/export/"
