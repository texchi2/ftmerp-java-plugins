// GetAuthorizedUsers.groovy
// FTM WiFi Enrollment — list authorized users with filters
// Delegator: ftmEnrollment (maps to ftm_enrollment PostgreSQL)

import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityQuery

def getFtmAuthorizedUsers() {
    def delegator = delegator.getDelegatorName().equals("ftmEnrollment") ?
        delegator : delegator.makeValidContext("ftmEnrollment", "main", context)

    // Use the ftmEnrollment delegator directly via the dispatcher
    def ftmDelegator = dispatcher.getDelegator().getDelegatorBaseName().equals("ftmEnrollment") ?
        dispatcher.getDelegator() :
        org.apache.ofbiz.entity.DelegatorFactory.getDelegator("ftmEnrollment")

    def conditions = []

    if (parameters.activeOnly != false) {
        // Default: only active users
        conditions.add(EntityCondition.makeCondition("active",
            EntityOperator.EQUALS, "Y"))
    }

    if (parameters.department) {
        conditions.add(EntityCondition.makeCondition("department",
            EntityOperator.EQUALS, parameters.department))
    }

    if (parameters.vlanTier == "VLAN10") {
        conditions.add(EntityCondition.makeCondition("ftmStaffVlan10",
            EntityOperator.EQUALS, "Y"))
    } else if (parameters.vlanTier == "VLAN20") {
        conditions.add(EntityCondition.makeCondition("ftmStaffVlan10",
            EntityOperator.EQUALS, "N"))
    }

    def condition = conditions ? EntityCondition.makeCondition(conditions) : null

    def query = EntityQuery.use(ftmDelegator)
        .from("FtmAuthorizedUser")
        .orderBy("employeeId")

    if (condition) {
        query = query.where(condition)
    }

    def users = query.queryList()

    result.userList = users ?: []
    result.userCount = result.userList.size()
    return result
}

return getFtmAuthorizedUsers()
