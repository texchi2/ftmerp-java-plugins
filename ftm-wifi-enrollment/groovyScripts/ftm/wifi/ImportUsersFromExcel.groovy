// ImportUsersFromExcel.groovy — FTM WiFi Enrollment Excel import
// Handles both service invocation and direct Groovy event (previewImport)
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import groovy.sql.Sql
import org.apache.ofbiz.base.util.UtilHttp

@groovy.transform.Field String JDBC_URL    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
@groovy.transform.Field String JDBC_USER   = "enrolladmin"
@groovy.transform.Field String JDBC_PASS   = System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026"
@groovy.transform.Field String JDBC_DRIVER = "org.postgresql.Driver"

@groovy.transform.Field
def getCellStr = { cell ->
    if (!cell) return ""
    switch (cell.getCellType()) {
        case CellType.STRING:  return cell.getStringCellValue()?.trim() ?: ""
        case CellType.NUMERIC: return String.valueOf((long) cell.getNumericCellValue())
        case CellType.BOOLEAN: return cell.getBooleanCellValue() ? "Y" : "N"
        default: return ""
    }
}

def parseSheet(sheet, sql, previewOnly) {
    def previewList  = []
    def errorList    = []
    def warnings     = []
    def addedCount   = 0
    def updatedCount = 0
    def skippedCount = 0
    def rowNum = 0

    sheet.each { row ->
        rowNum++
        if (rowNum == 1) return

        def empId = getCellStr(row.getCell(0))
        def fname = getCellStr(row.getCell(1))
        def uname = getCellStr(row.getCell(2))
        def dept  = getCellStr(row.getCell(3))
        def pos   = getCellStr(row.getCell(4))
        def quota = (getCellStr(row.getCell(5)) ?: "2") as Integer
        def vlan10 = getCellStr(row.getCell(6)).toUpperCase() in ["Y","YES","TRUE"]
        def notes = getCellStr(row.getCell(7))

        if (!empId && !uname) { skippedCount++; return }
        if (!empId) { errorList.add("Row ${rowNum}: Missing Employee ID"); return }
        if (!fname) { errorList.add("Row ${rowNum}: Missing Full Name"); return }
        if (!uname) { errorList.add("Row ${rowNum}: Missing Username"); return }

        def existing = sql.firstRow(
            "SELECT ftm_staff_vlan10 FROM authorized_users WHERE employee_id=?",
            [empId.toString()])

        def action = existing ? "UPDATE" : "ADD"
        if (existing && existing.ftm_staff_vlan10 != vlan10)
            warnings.add("Row ${rowNum}: VLAN change for [${uname}] — re-enrollment required.")

        if (!existing) {
            def dup = sql.firstRow(
                "SELECT employee_id FROM authorized_users WHERE username=?",
                [uname.toString()])
            if (dup) {
                errorList.add("Row ${rowNum}: Username [${uname}] taken by ${dup.employee_id}")
                return
            }
        }

        if (!previewOnly) {
            if (existing) {
                sql.execute("""UPDATE authorized_users SET
                    full_name=?, department=?, position=?,
                    device_quota=?, ftm_staff_vlan10=?, notes=?
                    WHERE employee_id=?""",
                    [fname.toString(), dept?.toString()?:null, pos?.toString()?:null,
                     quota, vlan10, notes?.toString()?:null, empId.toString()])
                updatedCount++
            } else {
                sql.execute("""INSERT INTO authorized_users
                    (employee_id,full_name,username,department,position,
                     device_quota,ftm_staff_vlan10,notes,active)
                    VALUES(?,?,?,?,?,?,?,?,TRUE)""",
                    [empId.toString(), fname.toString(), uname.toString(),
                     dept?.toString()?:null, pos?.toString()?:null,
                     quota, vlan10, notes?.toString()?:null])
                addedCount++
            }
        }
        previewList.add([employeeId:empId, fullName:fname, username:uname,
            department:dept, position:pos, deviceQuota:quota,
            ftmStaffVlan10:vlan10, action:action])
    }
    return [previewList:previewList, errorList:errorList, warnings:warnings,
            addedCount:addedCount, updatedCount:updatedCount, skippedCount:skippedCount]
}

// ── SERVICE entry point (invoke="importFtmUsersFromExcel") ──────────────────
def importFtmUsersFromExcel() {
    if (!parameters.uploadedFile) return error("No file uploaded")
    def bytes = new byte[parameters.uploadedFile.remaining()]
    parameters.uploadedFile.get(bytes)
    def workbook
    try {
        workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))
    } catch (Exception e) {
        return error("Cannot open Excel: ${e.message}")
    }
    def sql = Sql.newInstance(JDBC_URL, JDBC_USER, JDBC_PASS, JDBC_DRIVER)
    try {
        def result = parseSheet(workbook.getSheetAt(0), sql, parameters.previewOnly ?: false)
        return success(result)
    } finally {
        sql.close()
        workbook.close()
    }
}

// ── GROOVY EVENT entry point (invoke="previewImport") ─────────────────────
def previewImport() {
    // OFBiz already parsed multipart in UtilHttp.getParameterMap before calling event
    // The ByteBuffer is in the existing multiPartMap request attribute
    def multiPartMap = request.getAttribute("multiPartMap")

    def bb = multiPartMap?.get("uploadedFile")
    if (!bb) {
        // Last resort: try re-parsing
        def freshMap = UtilHttp.getMultiPartParameterMap(request)
        bb = freshMap?.get("uploadedFile")
    }
    if (!bb) {
        request.setAttribute("_ERROR_MESSAGE_",
            "No file. multiPartMap keys=${multiPartMap?.keySet()} contentType=${request.getContentType()} method=${request.getMethod()}")
        return "error"
    }


    def bytes = new byte[bb.remaining()]
    bb.get(bytes)
    // Store for CommitWifiImport
    request.getSession().setAttribute("importFileBytes", bytes)

    def workbook
    try {
        workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))
    } catch (Exception e) {
        request.setAttribute("_ERROR_MESSAGE_", "Cannot open Excel: ${e.message}")
        return "error"
    }

    def sql = Sql.newInstance(JDBC_URL, JDBC_USER, JDBC_PASS, JDBC_DRIVER)
    try {
        def result = parseSheet(workbook.getSheetAt(0), sql, true)
        def pl = result.previewList
        def el = result.errorList
        if (pl == null || pl.isEmpty()) {
            request.setAttribute("_ERROR_MESSAGE_",
                "parseSheet returned empty. errorList=" + el + " sheetRows=" + workbook.getSheetAt(0).getPhysicalNumberOfRows())
            return "error"
        }
        request.setAttribute("previewList",  pl)
        request.setAttribute("errorList",    el)
        request.setAttribute("warnings",     result.warnings)
        return "success"
    } finally {
        sql.close()
        workbook.close()
    }
}

// Router — called by OFBiz based on invoke= in controller/service
return previewImport()
