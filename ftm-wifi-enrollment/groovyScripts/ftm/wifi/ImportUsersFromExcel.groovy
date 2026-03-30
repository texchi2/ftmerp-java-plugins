// ImportUsersFromExcel.groovy
// FTM WiFi Enrollment — import staff roster from Excel (.xlsx or .xls)
// Uses Apache POI (bundled with OFBiz) — no internet dependencies
//
// Excel column mapping (0-indexed, row 0 = header, data from row 1):
//   0 = employee_id
//   1 = full_name
//   2 = username
//   3 = department
//   4 = position
//   5 = device_quota   (numeric, default 2)
//   6 = ftm_staff_vlan10  (Y/N or TRUE/FALSE)
//   7 = notes

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.base.util.UtilDateTime

def importFtmUsersFromExcel() {
    def ftmDelegator = org.apache.ofbiz.entity.DelegatorFactory.getDelegator("ftmEnrollment")
    def userLogin = context.userLogin
    def changedBy = userLogin?.getString("userLoginId") ?: "system"
    def previewOnly = parameters.previewOnly ?: false

    if (!parameters.uploadedFile) {
        return error("No file uploaded")
    }

    // Convert ByteBuffer to InputStream
    def byteBuffer = parameters.uploadedFile
    def bytes = new byte[byteBuffer.remaining()]
    byteBuffer.get(bytes)
    def inputStream = new java.io.ByteArrayInputStream(bytes)

    def workbook
    try {
        workbook = WorkbookFactory.create(inputStream)
    } catch (Exception e) {
        return error("Cannot open Excel file: ${e.message}. Ensure file is .xlsx or .xls format.")
    }

    def sheet = workbook.getSheetAt(0)
    def previewList = []
    def errorList   = []
    def warnings    = []
    def addedCount   = 0
    def updatedCount = 0
    def skippedCount = 0

    def getCellString = { cell ->
        if (cell == null) return ""
        switch (cell.getCellType()) {
            case CellType.STRING:  return cell.getStringCellValue()?.trim() ?: ""
            case CellType.NUMERIC: return String.valueOf((long) cell.getNumericCellValue())
            case CellType.BOOLEAN: return cell.getBooleanCellValue() ? "Y" : "N"
            default: return ""
        }
    }

    def rowNum = 0
    sheet.each { row ->
        rowNum++
        if (rowNum == 1) return // skip header

        def employeeId = getCellString(row.getCell(0))
        def fullName   = getCellString(row.getCell(1))
        def username   = getCellString(row.getCell(2))
        def department = getCellString(row.getCell(3))
        def position   = getCellString(row.getCell(4))
        def quotaStr   = getCellString(row.getCell(5))
        def vlanStr    = getCellString(row.getCell(6)).toUpperCase()
        def notes      = getCellString(row.getCell(7))

        // Skip blank rows
        if (!employeeId && !username) { skippedCount++; return }

        // Validate required
        if (!employeeId) { errorList.add("Row ${rowNum}: Missing Employee ID"); return }
        if (!fullName)   { errorList.add("Row ${rowNum}: Missing Full Name"); return }
        if (!username)   { errorList.add("Row ${rowNum}: Missing Username"); return }

        def quota  = quotaStr ? (quotaStr as Integer) : 2
        def vlan10 = (vlanStr == "Y" || vlanStr == "YES" || vlanStr == "TRUE")

        def rowData = [
            employeeId: employeeId, fullName: fullName, username: username,
            department: department, position: position, deviceQuota: quota,
            ftmStaffVlan10: vlan10, notes: notes, action: "?"
        ]

        // Check existing
        def existing = EntityQuery.use(ftmDelegator)
            .from("FtmAuthorizedUser")
            .where("employeeId", employeeId)
            .queryFirst()

        if (existing) {
            // Check VLAN change warning
            def oldVlan = existing.getBoolean("ftmStaffVlan10") ?: false
            if (oldVlan != vlan10) {
                warnings.add("Row ${rowNum}: VLAN tier change for [${username}] — " +
                    "device re-enrollment required.")
            }
            rowData.action = "UPDATE"
            if (!previewOnly) {
                ftmDelegator.withConnection("ftmEnrollmentDataSource") { conn ->
                    def stmt = conn.prepareStatement("""
                        UPDATE authorized_users SET
                            full_name=?, department=?, position=?,
                            device_quota=?, ftm_staff_vlan10=?, notes=?
                        WHERE employee_id=?
                    """)
                    stmt.setString(1, fullName)
                    stmt.setString(2, department ?: null)
                    stmt.setString(3, position ?: null)
                    stmt.setInt(4, quota)
                    stmt.setBoolean(5, vlan10)
                    stmt.setString(6, notes ?: null)
                    stmt.setString(7, employeeId)
                    stmt.executeUpdate()
                    stmt.close()
                }
                updatedCount++
            }
        } else {
            // Check username conflict
            def existingUser = EntityQuery.use(ftmDelegator)
                .from("FtmAuthorizedUser")
                .where("username", username)
                .queryFirst()
            if (existingUser) {
                errorList.add("Row ${rowNum}: Username [${username}] already taken by " +
                    existingUser.getString("employeeId"))
                return
            }
            rowData.action = "ADD"
            if (!previewOnly) {
                ftmDelegator.withConnection("ftmEnrollmentDataSource") { conn ->
                    def stmt = conn.prepareStatement("""
                        INSERT INTO authorized_users
                            (employee_id, full_name, username, department, position,
                             device_quota, ftm_staff_vlan10, notes, active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                    """)
                    stmt.setString(1, employeeId)
                    stmt.setString(2, fullName)
                    stmt.setString(3, username)
                    stmt.setString(4, department ?: null)
                    stmt.setString(5, position ?: null)
                    stmt.setInt(6, quota)
                    stmt.setBoolean(7, vlan10)
                    stmt.setString(8, notes ?: null)
                    stmt.executeUpdate()
                    stmt.close()
                }
                addedCount++
            }
        }
        previewList.add(rowData)
    }

    workbook.close()

    result.previewList   = previewList
    result.addedCount    = previewOnly ? 0 : addedCount
    result.updatedCount  = previewOnly ? 0 : updatedCount
    result.skippedCount  = skippedCount
    result.errorList     = errorList
    result.warnings      = warnings
    return result
}

return importFtmUsersFromExcel()
