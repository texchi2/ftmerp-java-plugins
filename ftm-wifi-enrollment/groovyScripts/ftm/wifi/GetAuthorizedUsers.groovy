// GetAuthorizedUsers.groovy
// FTM WiFi Enrollment — list authorized users with filters
// Uses groovy.sql.Sql for direct PostgreSQL access

import groovy.sql.Sql
import org.apache.ofbiz.base.util.Debug

def MODULE = "GetAuthorizedUsers"

def getFtmAuthorizedUsers() {
    def jdbcUrl  = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
    def jdbcUser = "enrolladmin"
    def jdbcPass = System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026"
    def jdbcDriver = "org.postgresql.Driver"

    def whereClauses = ["1=1"]
    def params = []

    // Default: active users only (unless explicitly false)
    if (parameters.activeOnly != false) {
        whereClauses.add("active = TRUE")
    }
    if (parameters.department) {
        whereClauses.add("department = ?")
        params.add(parameters.department)
    }
    if (parameters.vlanTier == "VLAN10") {
        whereClauses.add("ftm_staff_vlan10 = TRUE")
    } else if (parameters.vlanTier == "VLAN20") {
        whereClauses.add("ftm_staff_vlan10 = FALSE")
    }

    def whereStr = whereClauses.join(" AND ")
    def sqlStr = "SELECT * FROM authorized_users WHERE ${whereStr} ORDER BY employee_id"

    def userList = []
    def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
    try {
        sql.eachRow(sqlStr, params) { row ->
            userList.add([
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
            ])
        }
    } finally {
        sql.close()
    }

    return success([userList: userList, userCount: userList.size()])
}

return getFtmAuthorizedUsers()
