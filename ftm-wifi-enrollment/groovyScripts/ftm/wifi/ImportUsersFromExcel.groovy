// ImportUsersFromExcel.groovy
// FTM WiFi Enrollment — import staff roster from Excel (.xlsx or .xls)
// Apache POI bundled with OFBiz. groovy.sql.Sql for PostgreSQL.
//
// Excel column mapping (row 0 = header, data from row 1):
//   0=employee_id  1=full_name  2=username  3=department  4=position
//   5=device_quota  6=ftm_staff_vlan10 (Y/N)  7=notes

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import groovy.sql.Sql

def importFtmUsersFromExcel() {
    def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
    def jdbcUser   = "enrolladmin"
    def jdbcPass   = System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026"
    def jdbcDriver = "org.postgresql.Driver"
    def changedBy  = context.userLogin?.getString("userLoginId") ?: "system"
    def previewOnly = parameters.previewOnly ?: false

    if (!parameters.uploadedFile) return error("No file uploaded")

    def byteBuffer = parameters.uploadedFile
    def bytes = new byte[byteBuffer.remaining()]
    byteBuffer.get(bytes)

    def workbook
    try {
        workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))
    } catch (Exception e) {
        return error("Cannot open Excel file: ${e.message}")
    }

    def getCellStr = { cell ->
        if (!cell) return ""
        switch (cell.getCellType()) {
            case CellType.STRING:  return cell.getStringCellValue()?.trim() ?: ""
            case CellType.NUMERIC: return String.valueOf((long) cell.getNumericCellValue())
            case CellType.BOOLEAN: return cell.getBooleanCellValue() ? "Y" : "N"
            default: return ""
        }
    }

    def previewList  = []
    def errorList    = []
    def warnings     = []
    def addedCount   = 0
    def updatedCount = 0
    def skippedCount = 0

    def sheet = workbook.getSheetAt(0)
    def sql   = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)

    try {
        def rowNum = 0
        sheet.each { row ->
            rowNum++
            if (rowNum == 1) return  // skip header

            def empId  = getCellStr(row.getCell(0))
            def fname  = getCellStr(row.getCell(1))
            def uname  = getCellStr(row.getCell(2))
            def dept   = getCellStr(row.getCell(3))
            def pos    = getCellStr(row.getCell(4))
            def qStr   = getCellStr(row.getCell(5))
            def vStr   = getCellStr(row.getCell(6)).toUpperCase()
            def notes  = getCellStr(row.getCell(7))

            if (!empId && !uname) { skippedCount++; return }
            if (!empId) { errorList.add("Row ${rowNum}: Missing Employee ID"); return }
            if (!fname) { errorList.add("Row ${rowNum}: Missing Full Name"); return }
            if (!uname) { errorList.add("Row ${rowNum}: Missing Username"); return }

            def quota  = qStr ? (qStr as Integer) : 2
            def vlan10 = (vStr == "Y" || vStr == "YES" || vStr == "TRUE")

            def rowData = [empId: empId, fullName: fname, username: uname,
                           department: dept, position: pos, deviceQuota: quota,
                           ftmStaffVlan10: vlan10, notes: notes, action: "?"]

            def existing = sql.firstRow(
                "SELECT * FROM authorized_users WHERE employee_id = ?", [empId])

            if (existing) {
                if (existing.ftm_staff_vlan10 != vlan10) {
                    warnings.add("Row ${rowNum}: VLAN change for [${uname}] — device re-enrollment required.")
                }
                rowData.action = "UPDATE"
                if (!previewOnly) {
                    sql.execute("""
                        UPDATE authorized_users SET
                            full_name=?, department=?, position=?,
                            device_quota=?, ftm_staff_vlan10=?, notes=?
                        WHERE employee_id=?
                    """, [fname, dept ?: null, pos ?: null, quota, vlan10, notes ?: null, empId])
                    updatedCount++
                }
            } else {
                def dupUser = sql.firstRow(
                    "SELECT employee_id FROM authorized_users WHERE username = ?", [uname])
                if (dupUser) {
                    errorList.add("Row ${rowNum}: Username [${uname}] taken by ${dupUser.employee_id}")
                    return
                }
                rowData.action = "ADD"
                if (!previewOnly) {
                    sql.execute("""
                        INSERT INTO authorized_users
                            (employee_id, full_name, username, department, position,
                             device_quota, ftm_staff_vlan10, notes, active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                    """, [empId, fname, uname, dept ?: null, pos ?: null,
                          quota, vlan10, notes ?: null])
                    addedCount++
                }
            }
            previewList.add(rowData)
        }
    } finally {
        sql.close()
        workbook.close()
    }

    result.previewList  = previewList
    result.addedCount   = previewOnly ? 0 : addedCount
    result.updatedCount = previewOnly ? 0 : updatedCount
    result.skippedCount = skippedCount
    result.errorList    = errorList
    result.warnings     = warnings
    return result
}

return importFtmUsersFromExcel()
