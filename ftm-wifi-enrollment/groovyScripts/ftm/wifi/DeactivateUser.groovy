import groovy.sql.Sql

def deactivateFtmAuthorizedUser() {
    def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
    def jdbcUser   = "enrolladmin"
    def jdbcPass   = System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026"
    def jdbcDriver = "org.postgresql.Driver"
    def changedBy  = context.userLogin?.getString("userLoginId") ?: "system"

    if (!parameters.employeeId?.trim()) return error("Employee ID is required")
    def empId = parameters.employeeId.trim()

    def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
    try {
        def existing = sql.firstRow(
            "SELECT username, active, ftm_staff_vlan10 FROM authorized_users WHERE employee_id = ?",
            [empId])
        if (!existing)        return error("User [${empId}] not found")
        if (!existing.active) return error("User [${existing.username}] is already inactive")

        // DEACTIVATE only — set active=FALSE, do NOT delete
        sql.execute(
            "UPDATE authorized_users SET active = FALSE WHERE employee_id = ?",
            [empId])
        sql.execute("""
            INSERT INTO ftm_wifi_audit_log
                (employee_id, changed_by, field_name, old_value, new_value, action)
            VALUES (?, ?, 'active', 'true', 'false', 'DEACTIVATE')
        """, [empId, changedBy])

        def ssid = existing.ftm_staff_vlan10 ? "FTM-Staff (VLAN10)" : "FTM-Staff2 (VLAN20)"
        return success([
            radiusReminder: "ACTION REQUIRED: Disable FreeRADIUS user [${existing.username}] " +
                "on pfSense to revoke ${ssid} WiFi access."
        ])
    } finally {
        sql.close()
    }
}
return deactivateFtmAuthorizedUser()
