// GetEnrollmentCount.groovy
// FTM WiFi Enrollment — count active enrolled devices for a username
// enrolled_devices is managed by Flask portal (read-only from OFBiz)

import groovy.sql.Sql

def getFtmEnrollmentCount() {
    def jdbcUrl    = "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
    def jdbcUser   = "enrolladmin"
    def jdbcPass   = System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026"
    def jdbcDriver = "org.postgresql.Driver"

    if (!parameters.username?.trim()) return error("Username is required")

    def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
    try {
        def row = sql.firstRow("""
            SELECT COUNT(*) AS cnt
            FROM enrolled_devices ed
            JOIN authorized_users au ON au.id = ed.user_id
            WHERE au.username = ?
            AND ed.status NOT IN ('revoked', 'pending')
        """, [parameters.username.trim()])
        result.enrolledCount = row?.cnt ?: 0
    } finally {
        sql.close()
    }
    return result
}

return getFtmEnrollmentCount()
