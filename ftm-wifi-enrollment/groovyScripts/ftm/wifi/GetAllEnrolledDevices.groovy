// GetAllEnrolledDevices.groovy
// Returns all enrolled_devices with authorized_users info + quota usage
// Used by Device Roster and Device Admin screens

import groovy.sql.Sql

def getAllFtmEnrolledDevices() {
    def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
    def jdbcUser   = "enrolladmin"
    def jdbcPass   = System.getProperty("ftm.enrolladmin.password") ?: System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "MISSING_PASSWORD"
    def jdbcDriver = "org.postgresql.Driver"

    def statusFilter = parameters.statusFilter?.trim() ?: ""

    def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
    try {
        def query = """
            SELECT ed.id, ed.device_label, ed.serial_number, ed.cn, ed.status,
                   ed.request_time, ed.enrolled_at, ed.cert_expiry, ed.radius_active,
                   au.employee_id, au.full_name, au.username, au.device_quota,
                   au.ftm_staff_vlan10,
                   (SELECT COUNT(*) FROM enrolled_devices e2
                    WHERE e2.user_id = au.id
                    AND e2.status IN ('enrolled','approved')) AS active_device_count
            FROM enrolled_devices ed
            JOIN authorized_users au ON au.id = ed.user_id
        """
        def params = []
        if (statusFilter) {
            query += " WHERE ed.status = ?"
            params << statusFilter
        }
        query += " ORDER BY ed.request_time DESC"

        def devices = []
        sql.eachRow(query, params) { row ->
            devices << [
                id:             row.id,
                deviceLabel:    row.device_label ?: "(no label)",
                serialNumber:   row.serial_number ?: "",
                cn:             row.cn ?: "",
                status:         row.status,
                requestTime:    row.request_time?.toString()?.take(16) ?: "",
                enrolledAt:     row.enrolled_at?.toString()?.take(16) ?: "",
                certExpiry:     row.cert_expiry?.toString()?.take(10) ?: "",
                radiusActive:   row.radius_active ? "Yes" : "No",
                employeeId:     row.employee_id,
                fullName:       row.full_name,
                username:       row.username,
                deviceQuota:    row.device_quota,
                vlanTier:       row.ftm_staff_vlan10 ? "VLAN10" : "VLAN20",
                quotaDisplay:   "${row.active_device_count} / ${row.device_quota}"
            ]
        }
        result.deviceList  = devices
        result.deviceCount = devices.size()
    } finally {
        sql.close()
    }
    return result
}

return getAllFtmEnrolledDevices()
