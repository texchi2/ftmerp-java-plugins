// UpdateAuthorizedUser.groovy
// FTM WiFi Enrollment — update authorized user fields
// Emits vlanWarning when ftm_staff_vlan10 changes (requires device re-enrollment)

import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.base.util.UtilDateTime

def updateFtmAuthorizedUser() {
    def ftmDelegator = org.apache.ofbiz.entity.DelegatorFactory.getDelegator("ftmEnrollment")
    def userLogin = context.userLogin
    def changedBy = userLogin?.getString("userLoginId") ?: "system"

    if (!parameters.employeeId?.trim()) {
        return error("Employee ID is required")
    }

    def existing = EntityQuery.use(ftmDelegator)
        .from("FtmAuthorizedUser")
        .where("employeeId", parameters.employeeId.trim())
        .queryFirst()

    if (!existing) {
        return error("User with Employee ID [${parameters.employeeId}] not found")
    }

    def warnings = []
    def changes = [:]

    // Check VLAN tier change — requires device re-enrollment
    if (parameters.ftmStaffVlan10 != null) {
        def oldVlan = existing.getBoolean("ftmStaffVlan10") ?: false
        def newVlan = (parameters.ftmStaffVlan10 == true || parameters.ftmStaffVlan10 == "Y")
        if (oldVlan != newVlan) {
            def tierFrom = oldVlan ? "VLAN10 (FTM-Staff)" : "VLAN20 (FTM-Staff2)"
            def tierTo   = newVlan ? "VLAN10 (FTM-Staff)" : "VLAN20 (FTM-Staff2)"
            warnings.add("VLAN tier changed for [${existing.getString('username')}]: " +
                "${tierFrom} -> ${tierTo}. " +
                "Device re-enrollment required. Notify IT to revoke existing certs via pfSense.")
            changes.ftmStaffVlan10 = [old: oldVlan, new: newVlan]
        }
    }

    // Build dynamic UPDATE
    def setClauses = []
    def values = []

    if (parameters.fullName != null) {
        setClauses.add("full_name = ?")
        values.add(parameters.fullName)
        changes.fullName = [old: existing.getString("fullName"), new: parameters.fullName]
    }
    if (parameters.department != null) {
        setClauses.add("department = ?")
        values.add(parameters.department)
        changes.department = [old: existing.getString("department"), new: parameters.department]
    }
    if (parameters.position != null) {
        setClauses.add("position = ?")
        values.add(parameters.position)
    }
    if (parameters.deviceQuota != null) {
        setClauses.add("device_quota = ?")
        values.add(parameters.deviceQuota as Integer)
    }
    if (parameters.ftmStaffVlan10 != null) {
        setClauses.add("ftm_staff_vlan10 = ?")
        values.add(parameters.ftmStaffVlan10 == true || parameters.ftmStaffVlan10 == "Y")
    }
    if (parameters.notes != null) {
        setClauses.add("notes = ?")
        values.add(parameters.notes)
    }

    if (!setClauses) {
        return error("No fields to update")
    }

    values.add(parameters.employeeId.trim())
    def sql = "UPDATE authorized_users SET " + setClauses.join(", ") + " WHERE employee_id = ?"

    ftmDelegator.withConnection("ftmEnrollmentDataSource") { conn ->
        def stmt = conn.prepareStatement(sql)
        values.eachWithIndex { val, i ->
            stmt.setObject(i + 1, val)
        }
        stmt.executeUpdate()
        stmt.close()
    }

    // Audit each changed field
    def now = UtilDateTime.nowTimestamp()
    changes.each { fieldName, vals ->
        def audit = ftmDelegator.makeValue("FtmWifiAuditLog")
        audit.set("changedBy", changedBy)
        audit.set("changedAt", now)
        audit.set("employeeId", parameters.employeeId.trim())
        audit.set("fieldName", fieldName)
        audit.set("oldValue", vals.old?.toString())
        audit.set("newValue", vals.new?.toString())
        audit.set("action", "UPDATE")
        ftmDelegator.create(audit)
    }

    if (warnings) {
        result.vlanWarning = warnings.join("; ")
    }
    return result
}

return updateFtmAuthorizedUser()
