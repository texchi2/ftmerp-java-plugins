// GetAuthorizedUser.groovy — fetch single user by employeeId via direct JDBC
import groovy.sql.Sql

def getFtmAuthorizedUser() {
    def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
    def jdbcUser   = "enrolladmin"
    def jdbcPass   = System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026"
    def jdbcDriver = "org.postgresql.Driver"

    def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
    def user = null
    try {
        sql.eachRow("SELECT * FROM authorized_users WHERE employee_id = ?",
                    [parameters.employeeId]) { row ->
            user = [
                id:             row.id,
                employeeId:     row.employee_id,
                fullName:       row.full_name,
                username:       row.username,
                department:     row.department,
                position:       row.position,
                deviceQuota:    row.device_quota,
                ftmStaffVlan10: row.ftm_staff_vlan10,
                notes:          row.notes,
                active:         row.active
            ]
        }
    } finally {
        sql.close()
    }
    return success([user: user])
}
return getFtmAuthorizedUser()
