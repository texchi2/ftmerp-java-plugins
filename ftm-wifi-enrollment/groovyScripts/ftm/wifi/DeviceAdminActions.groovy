// DeviceAdminActions.groovy — screen actions for Device Admin view
// Loads pending devices (for approval queue) + all devices (filterable)
import groovy.sql.Sql

def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
def jdbcUser   = "enrolladmin"
def jdbcPass   = System.getProperty("ftm.enrolladmin.password") ?: System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "MISSING_PASSWORD"
def jdbcDriver = "org.postgresql.Driver"

def statusFilter = parameters.statusFilter ?: "all"
context.statusFilter = statusFilter

def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
try {
    // Pending queue
    def pendingDevices = []
    sql.eachRow("""
        SELECT ed.id, ed.device_label, ed.serial_number, ed.cn,
               ed.request_time, ed.request_ip,
               au.employee_id, au.full_name, au.username
        FROM enrolled_devices ed
        JOIN authorized_users au ON au.id = ed.user_id
        WHERE ed.status = 'pending'
        ORDER BY ed.request_time ASC
    """) { row ->
        pendingDevices << [
            id:           row.id,
            deviceLabel:  row.device_label ?: "(no label)",
            serialNumber: row.serial_number ?: "",
            cn:           row.cn ?: "",
            requestTime:  row.request_time?.toString()?.take(16) ?: "",
            requestIp:    row.request_ip ?: "",
            employeeId:   row.employee_id,
            fullName:     row.full_name,
            username:     row.username
        ]
    }
    context.pendingDevices = pendingDevices
    context.pendingCount   = pendingDevices.size()

    // All devices — filterable by status
    def allDevices = []
    def query = """
        SELECT ed.id, ed.device_label, ed.serial_number, ed.cn, ed.status,
               ed.request_time, ed.enrolled_at, ed.cert_expiry,
               ed.radius_active, ed.approved_by,
               au.employee_id, au.full_name, au.username, au.ftm_staff_vlan10
        FROM enrolled_devices ed
        JOIN authorized_users au ON au.id = ed.user_id
    """
    def params = []
    if (statusFilter != "all") {
        query += " WHERE ed.status = ?"
        params << statusFilter
    }
    query += " ORDER BY ed.request_time DESC"

    sql.eachRow(query, params) { row ->
        allDevices << [
            id:           row.id,
            deviceLabel:  row.device_label ?: "(no label)",
            serialNumber: row.serial_number ?: "",
            cn:           row.cn ?: "",
            status:       row.status,
            requestTime:  row.request_time?.toString()?.take(16) ?: "",
            enrolledAt:   row.enrolled_at?.toString()?.take(16) ?: "",
            certExpiry:   row.cert_expiry?.toString()?.take(10) ?: "",
            radiusActive: row.radius_active ? "Yes" : "No",
            approvedBy:   row.approved_by ?: "",
            employeeId:   row.employee_id,
            fullName:     row.full_name,
            username:     row.username,
            vlanTier:     row.ftm_staff_vlan10 ? "VLAN10" : "VLAN20"
        ]
    }
    context.allDevices     = allDevices
    context.allDeviceCount = allDevices.size()
} finally {
    sql.close()
}
