// EditUserActions.groovy — load user for edit screen, or blank for add
import groovy.sql.Sql

def employeeId = parameters.employeeId
context.user           = null
context.enrolledDevices = []
context.quotaDisplay   = "0 / ?"

if (employeeId?.trim()) {
    def sql = Sql.newInstance(
        "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment",
        "enrolladmin",
        System.getProperty("ftm.enrolladmin.password") ?: System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "MISSING_PASSWORD",
        "org.postgresql.Driver"
    )
    try {
        sql.eachRow("SELECT * FROM authorized_users WHERE employee_id = ?",
                    [employeeId.trim()]) { row ->
            context.user = [
                id:             row.id,
                employeeId:     row.employee_id,
                fullName:       row.full_name,
                username:       row.username,
                department:     row.department,
                position:       row.position,
                deviceQuota:    row.device_quota,
                ftmStaffVlan10: row.ftm_staff_vlan10 ? "true" : "false",
                notes:          row.notes,
                active:         row.active
            ]
        }

        if (context.user) {
            def devices = []
            sql.eachRow("""
                SELECT ed.id, ed.device_label, ed.serial_number, ed.cn, ed.status,
                       ed.request_time, ed.enrolled_at, ed.cert_expiry, ed.radius_active
                FROM enrolled_devices ed
                JOIN authorized_users au ON au.id = ed.user_id
                WHERE au.employee_id = ?
                ORDER BY ed.request_time DESC
            """, [employeeId.trim()]) { row ->
                devices << [
                    id:           row.id,
                    deviceLabel:  row.device_label ?: "(no label)",
                    serialNumber: row.serial_number ?: "",
                    cn:           row.cn ?: "",
                    status:       row.status,
                    requestTime:  row.request_time?.toString()?.take(16) ?: "",
                    enrolledAt:   row.enrolled_at?.toString()?.take(16) ?: "",
                    certExpiry:   row.cert_expiry?.toString()?.take(10) ?: "",
                    radiusActive: row.radius_active ? "Yes" : "No"
                ]
            }
            context.enrolledDevices = devices
            def activeCount = devices.count { it.status in ['enrolled', 'approved'] }
            context.quotaDisplay = "${activeCount} / ${context.user.deviceQuota}"
        }
    } finally {
        sql.close()
    }
}

context.titleProperty = context.user?.fullName ?
    "Edit User: ${context.user.fullName}" : "Add New User"
