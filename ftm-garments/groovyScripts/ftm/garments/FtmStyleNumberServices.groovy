package org.apache.ofbiz.ftm.garments

// No method parameter: OFBiz's GroovyEngine calls invokeMethod(name, EMPTY_ARGS), so a declared `Map parameters`
// argument arrives as null and shadows the script binding that holds the IN map (bug-1769).
// Each service returns only the OUT values its services.xml entry declares.

def getFtmStyle() {
    def styleValue = delegator.findOne("FtmStyleNumber", [styleNumberId: parameters.styleNumberId], true)
    if (!styleValue) return error("Style Number not found")

    return success([
        styleNumber: styleValue.styleNumber,
        buyer: styleValue.buyer,
        description: styleValue.description,
        productType: styleValue.productType,
        productCategory: styleValue.productCategory,
        season: styleValue.season,
        status: styleValue.status
    ])
}

def createFtmStyleNumber() {
    def styleNumberId = delegator.getNextSeqId("FtmStyleNumber")
    def styleMap = [
        styleNumberId: styleNumberId,
        styleNumber: parameters.styleNumber,
        buyer: parameters.buyer,
        description: parameters.description,
        productType: parameters.productType,
        productCategory: parameters.productCategory,
        season: parameters.season,
        status: parameters.status
    ]
    delegator.create("FtmStyleNumber", styleMap)
    return success([styleNumberId: styleNumberId])
}

def updateFtmStyleNumber() {
    // not from the cache: a cached value cannot be modified
    def styleValue = delegator.findOne("FtmStyleNumber", [styleNumberId: parameters.styleNumberId], false)
    if (!styleValue) return error("Style Number not found")

    def updateMap = [:]
    if (parameters.styleNumber) updateMap.styleNumber = parameters.styleNumber
    if (parameters.buyer) updateMap.buyer = parameters.buyer
    if (parameters.description) updateMap.description = parameters.description
    if (parameters.productType) updateMap.productType = parameters.productType
    if (parameters.productCategory) updateMap.productCategory = parameters.productCategory
    if (parameters.season) updateMap.season = parameters.season
    if (parameters.status) updateMap.status = parameters.status

    if (updateMap) {
        styleValue.setNonPKFields(updateMap)
        delegator.store(styleValue)
    }
    return success()
}

def deleteFtmStyleNumber() {
    def styleValue = delegator.findOne("FtmStyleNumber", [styleNumberId: parameters.styleNumberId], false)
    if (!styleValue) return error("Style Number not found")

    delegator.removeValue(styleValue)
    return success()
}
