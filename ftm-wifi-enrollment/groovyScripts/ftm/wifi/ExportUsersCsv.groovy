// ExportUsersCsv.groovy — write CSV directly to HttpServletResponse
import groovy.sql.Sql
import javax.servlet.http.HttpServletResponse

def response = request.getAttribute("javax.servlet.http.HttpServletResponse") ?:
               context.get("response")

if (!response) {
    // Try alternate context key
    response = context.response
}

response.contentType = "text/csv"
response.setHeader("Content-Disposition",
    "attachment; filename=\"ftm-wifi-users.csv\"")
response.characterEncoding = "UTF-8"

def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
def jdbcUser   = "enrolladmin"
def jdbcPass   = System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026"
def jdbcDriver = "org.postgresql.Driver"

def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
def writer = response.writer

try {
    writer.println("Employee ID,Full Name,Username,Department,Position,Device Quota,WiFi Tier,Active")
    sql.eachRow("SELECT * FROM authorized_users ORDER BY employee_id") { row ->
        def tier   = row.ftm_staff_vlan10 ? "FTM-Staff (VLAN10)" : "FTM-Staff2 (VLAN20)"
        def active = row.active ? "Yes" : "No"
        def quota  = row.device_quota ?: 2
        // Escape commas in fields
        def fields = [
            row.employee_id,
            "\"${row.full_name}\"",
            row.username,
            "\"${row.department ?: ''}\"",
            "\"${row.position ?: ''}\"",
            quota,
            tier,
            active
        ]
        writer.println(fields.join(","))
    }
    writer.flush()
} finally {
    sql.close()
}

// Return null to tell OFBiz the response is already handled
return "success"
