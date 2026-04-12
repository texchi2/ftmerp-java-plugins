// GetStyles.groovy — uses OFBiz delegator (FtmStyleNumber is in Derby)
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityQuery

def getFtmStyles() {
    def conditions = []

    if (parameters.buyer)       conditions.add(EntityCondition.makeCondition("buyer",       EntityOperator.EQUALS, parameters.buyer))
    if (parameters.productType) conditions.add(EntityCondition.makeCondition("productType", EntityOperator.EQUALS, parameters.productType))
    if (parameters.status)      conditions.add(EntityCondition.makeCondition("status",      EntityOperator.EQUALS, parameters.status))

    def query = EntityQuery.use(delegator).from("FtmStyleNumber")
    if (conditions) query = query.where(EntityCondition.makeCondition(conditions, EntityOperator.AND))

    def styleList = query.queryList()?.collect { row ->
        [styleId: row.styleNumberId, buyer: row.buyer,
         productType: row.productType, status: row.status,
         description: row.description]
    } ?: []

    return success([styleList: styleList, styleCount: styleList.size()])
}
return getFtmStyles()
