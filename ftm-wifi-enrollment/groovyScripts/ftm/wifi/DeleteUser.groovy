import groovy.sql.Sql

def deleteFtmAuthorizedUser() {
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
            "SELECT username FROM authorized_users WHERE employee_id = ?", [empId])
        if (!existing) return error("User [${empId}] not found")

        // Audit log first, then DELETE
        sql.execute("""
            INSERT INTO ftm_wifi_audit_log
                (employee_id, changed_by, field_name, old_value, new_value, action)
            VALUES (?, ?, 'record', 'exists', 'deleted', 'DELETE')
        """, [empId, changedBy])
        sql.execute(
            "DELETE FROM authorized_users WHERE employee_id = ?", [empId])

        return success([message: "User [${existing.username}] permanently deleted"])
    } finally {
        sql.close()
    }
}
return deleteFtmAuthorizedUser()
