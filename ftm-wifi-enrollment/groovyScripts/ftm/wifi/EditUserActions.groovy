// EditUserActions.groovy — load user for edit screen, or blank for add
import groovy.sql.Sql

def employeeId = parameters.employeeId
if (employeeId) {
    def sql = Sql.newInstance(
        "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment",
        "enrolladmin",
        System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026",
        "org.postgresql.Driver"
    )
    try {
        sql.eachRow("SELECT * FROM authorized_users WHERE employee_id = ?",
                    [employeeId]) { row ->
            context.user = [
                id: row.id, employeeId: row.employee_id,
                fullName: row.full_name, username: row.username,
                department: row.department, position: row.position,
                deviceQuota: row.device_quota,
                ftmStaffVlan10: row.ftm_staff_vlan10,
                notes: row.notes, active: row.active
            ]
        }
    } finally {
        sql.close()
    }
}
context.titleProperty = context.user?.fullName ?
    "Edit User: ${context.user.fullName}" : "Add New User"
