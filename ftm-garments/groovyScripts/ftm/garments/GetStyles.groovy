import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityQuery

def getFtmStyles() {
    def conditions = []

    if (parameters.buyer) {
        def buyerVal = parameters.buyer
        if (parameters.ignoreCase == "Y") {
            conditions.add(EntityCondition.makeCondition(
                "buyer", EntityOperator.LIKE,
                "%" + buyerVal.toUpperCase() + "%"))
        } else {
            conditions.add(EntityCondition.makeCondition(
                "buyer", EntityOperator.EQUALS, buyerVal))
        }
    }
    if (parameters.productType)
        conditions.add(EntityCondition.makeCondition(
            "productType", EntityOperator.EQUALS, parameters.productType))
    if (parameters.status)
        conditions.add(EntityCondition.makeCondition(
            "status", EntityOperator.EQUALS, parameters.status))

    def query = EntityQuery.use(delegator).from("FtmStyleNumber")
    if (conditions)
        query = query.where(EntityCondition.makeCondition(
            conditions, EntityOperator.AND))

    def styleList = query.queryList()?.collect { row ->
        [styleNumberId: row.styleNumberId, styleNumber: row.styleNumber,
         buyer: row.buyer, productType: row.productType,
         status: row.status, description: row.description]
    } ?: []

    return success([styleList: styleList, styleCount: styleList.size()])
}
return getFtmStyles()
