// DeactivateUser.groovy
// FTM WiFi Enrollment — set active=false
// Returns radiusReminder message for IT to revoke on pfSense FreeRADIUS

import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.base.util.UtilDateTime

def deactivateFtmAuthorizedUser() {
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

    if (existing.getString("active") == "N") {
        return error("User [${existing.getString('username')}] is already inactive")
    }

    def username = existing.getString("username")
    def vlan10 = existing.getBoolean("ftmStaffVlan10") ?: false
    def ssid = vlan10 ? "FTM-Staff (VLAN10)" : "FTM-Staff2 (VLAN20)"

    // Set active = false
    ftmDelegator.withConnection("ftmEnrollmentDataSource") { conn ->
        def stmt = conn.prepareStatement(
            "UPDATE authorized_users SET active = FALSE WHERE employee_id = ?")
        stmt.setString(1, parameters.employeeId.trim())
        stmt.executeUpdate()
        stmt.close()
    }

    // Audit
    def audit = ftmDelegator.makeValue("FtmWifiAuditLog")
    audit.set("changedBy", changedBy)
    audit.set("changedAt", UtilDateTime.nowTimestamp())
    audit.set("employeeId", parameters.employeeId.trim())
    audit.set("fieldName", "active")
    audit.set("oldValue", "true")
    audit.set("newValue", "false")
    audit.set("action", "DEACTIVATE")
    ftmDelegator.create(audit)

    // FreeRADIUS revocation reminder — must be done manually on pfSense
    result.radiusReminder = (
        "ACTION REQUIRED: Disable FreeRADIUS user [${username}] on pfSense to " +
        "revoke ${ssid} WiFi access immediately.\n" +
        "pfSense path: Services > FreeRADIUS > Users > find [${username}] > set Auth-Type := Reject\n" +
        "Also update enrolled_devices: SET status='revoked' WHERE user_id = <id> in ftm_enrollment DB."
    )

    return result
}

return deactivateFtmAuthorizedUser()
