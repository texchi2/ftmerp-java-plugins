// UpdateAuthorizedUser.groovy
// FTM WiFi Enrollment — update authorized user
// Emits vlanWarning when ftm_staff_vlan10 changes

import groovy.sql.Sql

def updateFtmAuthorizedUser() {
    def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
    def jdbcUser   = "enrolladmin"
    def jdbcPass   = System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026"
    def jdbcDriver = "org.postgresql.Driver"
    def changedBy  = context.userLogin?.getString("userLoginId") ?: "system"

    if (!parameters.employeeId?.trim()) return error("Employee ID is required")
    def empId = parameters.employeeId.trim()
    def vlanWarning = null

    def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
    try {
        def existing = sql.firstRow("SELECT * FROM authorized_users WHERE employee_id = ?", [empId])
        if (!existing) return error("User with Employee ID [${empId}] not found")

        def setClauses = []
        def values     = []
        def auditRows  = []

        if (parameters.fullName != null) {
            setClauses.add("full_name = ?")
            values.add(parameters.fullName)
            auditRows.add([empId, changedBy, "fullName", existing.full_name, parameters.fullName])
        }
        if (parameters.department != null) {
            setClauses.add("department = ?")
            values.add(parameters.department)
            auditRows.add([empId, changedBy, "department", existing.department, parameters.department])
        }
        if (parameters.position != null) {
            setClauses.add("position = ?")
            values.add(parameters.position)
        }
        if (parameters.deviceQuota != null) {
            setClauses.add("device_quota = ?")
            values.add(parameters.deviceQuota as Integer)
        }
        if (parameters.ftmStaffVlan10 != null) {
            def newVlan = (parameters.ftmStaffVlan10 == true || parameters.ftmStaffVlan10 == "Y")
            setClauses.add("ftm_staff_vlan10 = ?")
            values.add(newVlan)
            // VLAN tier change warning
            if (existing.ftm_staff_vlan10 != newVlan) {
                def from = existing.ftm_staff_vlan10 ? "VLAN10 (FTM-Staff)" : "VLAN20 (FTM-Staff2)"
                def to   = newVlan ? "VLAN10 (FTM-Staff)" : "VLAN20 (FTM-Staff2)"
                vlanWarning = "VLAN tier changed for [${existing.username}]: ${from} -> ${to}. " +
                    "Device re-enrollment required. Notify IT to revoke existing certs."
                auditRows.add([empId, changedBy, "ftmStaffVlan10",
                               existing.ftm_staff_vlan10.toString(), newVlan.toString()])
            }
        }
        if (parameters.notes != null) {
            setClauses.add("notes = ?")
            values.add(parameters.notes)
        }

        if (!setClauses) return error("No fields to update")

        values.add(empId)
        sql.execute("UPDATE authorized_users SET " + setClauses.join(", ") + " WHERE employee_id = ?", values)

        // Write audit rows
        auditRows.each { r ->
            sql.execute("""
                INSERT INTO ftm_wifi_audit_log
                    (employee_id, changed_by, field_name, old_value, new_value, action)
                VALUES (?, ?, ?, ?, ?, 'UPDATE')
            """, [r[0], r[1], r[2], r[3], r[4]])
        }
    } finally {
        sql.close()
    }
    return success([vlanWarning: vlanWarning ?: ""])
}

return updateFtmAuthorizedUser()
