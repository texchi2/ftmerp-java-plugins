// GetEnrollmentCount.groovy
// FTM WiFi Enrollment — count enrolled devices for a given username
// Reads from enrolled_devices table (managed by Flask portal, read-only from OFBiz)

def getFtmEnrollmentCount() {
    def ftmDelegator = org.apache.ofbiz.entity.DelegatorFactory.getDelegator("ftmEnrollment")

    if (!parameters.username?.trim()) {
        return error("Username is required")
    }

    def count = 0
    ftmDelegator.withConnection("ftmEnrollmentDataSource") { conn ->
        def stmt = conn.prepareStatement("""
            SELECT COUNT(*) FROM enrolled_devices ed
            JOIN authorized_users au ON au.id = ed.user_id
            WHERE au.username = ?
            AND ed.status NOT IN ('revoked', 'pending')
        """)
        stmt.setString(1, parameters.username.trim())
        def rs = stmt.executeQuery()
        if (rs.next()) {
            count = rs.getInt(1)
        }
        rs.close()
        stmt.close()
    }

    result.enrolledCount = count
    return result
}

return getFtmEnrollmentCount()
