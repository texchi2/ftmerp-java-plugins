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

// Get file from session (stored during preview)
def sessionFile = request.getSession().getAttribute("importFileBytes")
if (!sessionFile) {
    // Try multiPartMap
    def multiPartMap = request.getAttribute("multiPartMap") ?:
        UtilHttp.getMultiPartParameterMap(request)
    def bb = multiPartMap?.get("uploadedFile")
    if (bb) {
        sessionFile = new byte[bb.remaining()]
        bb.get(sessionFile)
    }
}
if (!sessionFile) {
    request.setAttribute("_ERROR_MESSAGE_", "Session expired — please re-upload the file")
    return "error"
}

def workbook
try {
    workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(sessionFile))
} catch (Exception e) {
    request.setAttribute("_ERROR_MESSAGE_", "Cannot open Excel: ${e.message}")
    return "error"
}

def addedCount = 0
def updatedCount = 0
def skippedCount = 0
def errorList = []
def warnings = []
def changedBy = request.getSession().getAttribute("userLogin")?.get("userLoginId") ?: "admin"

def sql = Sql.newInstance(JDBC_URL, JDBC_USER, JDBC_PASS, JDBC_DRIVER)
try {
    def rowNum = 0
    workbook.getSheetAt(0).each { row ->
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

        if (existing) {
            if (existing.ftm_staff_vlan10 != vlan10)
                warnings.add("VLAN change for [${uname}] — re-enrollment required.")
            sql.execute("""UPDATE authorized_users SET
                full_name=?, department=?, position=?,
                device_quota=?, ftm_staff_vlan10=?, notes=?
                WHERE employee_id=?""",
                [fname.toString(), dept?.toString()?:null, pos?.toString()?:null,
                 quota, vlan10, notes?.toString()?:null, empId.toString()])
            updatedCount++
        } else {
            def dup = sql.firstRow(
                "SELECT employee_id FROM authorized_users WHERE username=?",
                [uname.toString()])
            if (dup) {
                errorList.add("Row ${rowNum}: Username [${uname}] taken by ${dup.employee_id}")
                return
            }
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
} finally {
    sql.close()
    workbook.close()
    request.getSession().removeAttribute("importFileBytes")
}

request.setAttribute("addedCount",   addedCount)
request.setAttribute("updatedCount", updatedCount)
request.setAttribute("skippedCount", skippedCount)
request.setAttribute("errorList",    errorList)
request.setAttribute("warnings",     warnings)
return "success"
