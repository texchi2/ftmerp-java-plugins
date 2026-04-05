import groovy.sql.Sql

response.contentType = "text/csv;charset=UTF-8"
response.setHeader("Content-Disposition", "attachment; filename=\"ftm-wifi-users.csv\"")
response.characterEncoding = "UTF-8"

// Tell OFBiz controller NOT to process response further
request.setAttribute("_CONTROL_PATH_", null)
request.setAttribute("_RESPONSE_ALREADY_WRITTEN_", true)

def sql = Sql.newInstance(
    "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment",
    "enrolladmin",
    System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026",
    "org.postgresql.Driver"
)
def writer = response.writer
try {
    writer.println("Employee ID,Full Name,Username,Department,Position,Device Quota,WiFi Tier,Active")
    sql.eachRow("SELECT * FROM authorized_users WHERE active = TRUE ORDER BY employee_id") { row ->
        def tier  = row.ftm_staff_vlan10 ? "FTM-Staff (VLAN10)" : "FTM-Staff2 (VLAN20)"
        def quota = row.device_quota ?: 2
        writer.println([
            row.employee_id,
            "\"${row.full_name}\"",
            row.username,
            "\"${row.department ?: ''}\"",
            "\"${row.position ?: ''}\"",
            quota, tier, "Yes"
        ].join(","))
    }
    writer.flush()
    response.flushBuffer()
} finally {
    sql.close()
}
return "success"
