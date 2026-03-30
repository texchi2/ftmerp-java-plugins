// CreateAuthorizedUser.groovy
// FTM WiFi Enrollment — create a new authorized user
// Validates uniqueness of employeeId and username before insert

import groovy.sql.Sql
import org.apache.ofbiz.base.util.UtilDateTime

def createFtmAuthorizedUser() {
    def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
    def jdbcUser   = "enrolladmin"
    def jdbcPass   = System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026"
    def jdbcDriver = "org.postgresql.Driver"
    def changedBy  = context.userLogin?.getString("userLoginId") ?: "system"

    if (!parameters.employeeId?.trim()) return error("Employee ID is required")
    if (!parameters.fullName?.trim())   return error("Full Name is required")
    if (!parameters.username?.trim())   return error("Username is required")

    def empId    = parameters.employeeId.trim()
    def uname    = parameters.username.trim()
    def quota    = parameters.deviceQuota ? (parameters.deviceQuota as Integer) : 2
    def vlan10   = (parameters.ftmStaffVlan10 == true || parameters.ftmStaffVlan10 == "Y")

    def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
    try {
        // Duplicate checks
        def existById = sql.firstRow("SELECT id FROM authorized_users WHERE employee_id = ?", [empId])
        if (existById) return error("Employee ID [${empId}] already exists")

        def existByUser = sql.firstRow("SELECT id FROM authorized_users WHERE username = ?", [uname])
        if (existByUser) return error("Username [${uname}] already exists")

        // Insert and return new id
        def newId = null
        sql.eachRow("""
            INSERT INTO authorized_users
                (employee_id, full_name, username, department, position,
                 device_quota, ftm_staff_vlan10, notes, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
            RETURNING id
        """, [empId, parameters.fullName.trim(), uname,
              parameters.department ?: null,
              parameters.position   ?: null,
              quota, vlan10,
              parameters.notes      ?: null]) { row ->
            newId = row.id
        }

        // Audit log
        sql.execute("""
            INSERT INTO ftm_wifi_audit_log
                (changed_by, employee_id, field_name, old_value, new_value, action)
            VALUES (?, ?, 'ALL', NULL, ?, 'CREATE')
        """, [changedBy, empId, "CREATED: username=${uname}"])

        result.newId = newId
    } finally {
        sql.close()
    }
    return result
}

return createFtmAuthorizedUser()
