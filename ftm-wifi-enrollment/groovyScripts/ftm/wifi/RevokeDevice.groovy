// RevokeDevice.groovy — revoke an enrolled device (disables RADIUS)
import groovy.sql.Sql

def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
def jdbcUser   = "enrolladmin"
def jdbcPass   = System.getProperty("ftm.enrolladmin.password") ?: System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "MISSING_PASSWORD"
def jdbcDriver = "org.postgresql.Driver"

if (!parameters.deviceId) { return error("Device ID is required") }
def deviceId = parameters.deviceId as Long

def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
try {
    def existing = sql.firstRow("""
        SELECT ed.id, ed.status, ed.device_label, au.username
        FROM enrolled_devices ed
        JOIN authorized_users au ON au.id = ed.user_id
        WHERE ed.id = ?
    """, [deviceId])
    if (!existing) { return error("Device [${deviceId}] not found") }
    if (existing.status == 'revoked') { return error("Device already revoked") }

    sql.execute("""
        UPDATE enrolled_devices
        SET status = 'revoked', radius_active = FALSE
        WHERE id = ?
    """, [deviceId])

    result.message = "Device '${existing.device_label}' (${existing.username}) revoked — RADIUS disabled"
} finally {
    sql.close()
}
