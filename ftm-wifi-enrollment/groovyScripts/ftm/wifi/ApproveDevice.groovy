// ApproveDevice.groovy — approve a pending device enrollment
// No invoke= in service def; script.run() used; return Map explicitly.
import groovy.sql.Sql

def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
def jdbcUser   = "enrolladmin"
def jdbcPass   = System.getProperty("ftm.enrolladmin.password") ?: System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "MISSING_PASSWORD"
def jdbcDriver = "org.postgresql.Driver"
def approvedBy = context?.get("userLogin")?.getString("userLoginId") ?: "ofbiz-admin"

if (!parameters.deviceId) {
    return [responseMessage: "error", errorMessage: "Device ID is required"]
}
def deviceId = parameters.deviceId as Long

def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
try {
    def existing = sql.firstRow(
        "SELECT id, status, device_label FROM enrolled_devices WHERE id = ?", [deviceId])
    if (!existing) {
        return [responseMessage: "error", errorMessage: "Device [${deviceId}] not found"]
    }
    if (existing.status != 'pending') {
        return [responseMessage: "error", errorMessage: "Device not pending (current: ${existing.status})"]
    }

    sql.execute("""
        UPDATE enrolled_devices
        SET status = 'approved', approved_by = ?, approved_at = NOW()
        WHERE id = ?
    """, [approvedBy.toString(), deviceId])

    return [responseMessage: "success", message: "Device '${existing.device_label}' approved by ${approvedBy}"]
} finally {
    sql.close()
}
