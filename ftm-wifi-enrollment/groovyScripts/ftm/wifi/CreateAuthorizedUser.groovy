// CreateAuthorizedUser.groovy
// FTM WiFi Enrollment — create a new authorized user
// Validates uniqueness of employeeId and username before insert

import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityQuery

def createFtmAuthorizedUser() {
    def ftmDelegator = org.apache.ofbiz.entity.DelegatorFactory.getDelegator("ftmEnrollment")
    def userLogin = context.userLogin

    // Validate required fields
    if (!parameters.employeeId?.trim()) {
        return error("Employee ID is required")
    }
    if (!parameters.fullName?.trim()) {
        return error("Full Name is required")
    }
    if (!parameters.username?.trim()) {
        return error("Username is required")
    }

    // Check duplicate employeeId
    def existingById = EntityQuery.use(ftmDelegator)
        .from("FtmAuthorizedUser")
        .where("employeeId", parameters.employeeId.trim())
        .queryFirst()
    if (existingById) {
        return error("Employee ID [${parameters.employeeId}] already exists")
    }

    // Check duplicate username
    def existingByUser = EntityQuery.use(ftmDelegator)
        .from("FtmAuthorizedUser")
        .where("username", parameters.username.trim())
        .queryFirst()
    if (existingByUser) {
        return error("Username [${parameters.username}] already exists")
    }

    // Build new record using native JDBC since OFBiz sequencer won't work for external DB
    def sql = """
        INSERT INTO authorized_users
            (employee_id, full_name, username, department, position,
             device_quota, ftm_staff_vlan10, notes, active)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
        RETURNING id
    """

    def vlan10 = (parameters.ftmStaffVlan10 == true || parameters.ftmStaffVlan10 == "Y") ? true : false
    def quota = parameters.deviceQuota ? parameters.deviceQuota as Integer : 2

    def newId = null
    ftmDelegator.withConnection("ftmEnrollmentDataSource") { conn ->
        def stmt = conn.prepareStatement(sql)
        stmt.setString(1, parameters.employeeId.trim())
        stmt.setString(2, parameters.fullName.trim())
        stmt.setString(3, parameters.username.trim())
        stmt.setString(4, parameters.department ?: null)
        stmt.setString(5, parameters.position ?: null)
        stmt.setInt(6, quota)
        stmt.setBoolean(7, vlan10)
        stmt.setString(8, parameters.notes ?: null)
        def rs = stmt.executeQuery()
        if (rs.next()) {
            newId = rs.getLong(1)
        }
        rs.close()
        stmt.close()
    }

    // Write audit log
    def audit = ftmDelegator.makeValue("FtmWifiAuditLog")
    audit.set("changedBy", userLogin?.getString("userLoginId") ?: "system")
    audit.set("changedAt", org.apache.ofbiz.base.util.UtilDateTime.nowTimestamp())
    audit.set("employeeId", parameters.employeeId.trim())
    audit.set("fieldName", "ALL")
    audit.set("oldValue", null)
    audit.set("newValue", "CREATED: username=${parameters.username}")
    audit.set("action", "CREATE")
    ftmDelegator.create(audit)

    result.newId = newId
    return result
}

return createFtmAuthorizedUser()
