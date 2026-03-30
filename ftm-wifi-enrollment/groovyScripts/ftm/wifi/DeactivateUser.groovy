// DeactivateUser.groovy
// FTM WiFi Enrollment — set active=false + FreeRADIUS reminder

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
        def existing = sql.firstRow("SELECT * FROM authorized_users WHERE employee_id = ?", [empId])
        if (!existing)             return error("User [${empId}] not found")
        if (!existing.active)      return error("User [${existing.username}] is already inactive")

        def uname  = existing.username
        def vlan10 = existing.ftm_staff_vlan10
        def ssid   = vlan10 ? "FTM-Staff (VLAN10)" : "FTM-Staff2 (VLAN20)"

        sql.execute("UPDATE authorized_users SET active = FALSE WHERE employee_id = ?", [empId])

        sql.execute("""
            INSERT INTO ftm_wifi_audit_log
                (employee_id, changed_by, field_name, old_value, new_value, action)
            VALUES (?, ?, 'active', 'true', 'false', 'DEACTIVATE')
        """, [empId, changedBy])

        result.radiusReminder =
            "ACTION REQUIRED: Disable FreeRADIUS user [${uname}] on pfSense to " +
            "revoke ${ssid} WiFi access immediately.\n" +
            "pfSense path: Services > FreeRADIUS > Users > [${uname}] > Auth-Type := Reject\n" +
            "Also: UPDATE enrolled_devices SET status='revoked' WHERE user_id = <id> in ftm_enrollment DB."
    } finally {
        sql.close()
    }
    return result
}

return deactivateFtmAuthorizedUser()
