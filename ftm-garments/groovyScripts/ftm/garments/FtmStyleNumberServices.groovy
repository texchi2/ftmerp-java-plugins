package org.apache.ofbiz.ftm.garments

import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

def getFtmStyle(Map parameters) {
    def styleValue = delegator.findOne("FtmStyleNumber", EntityCondition.makeCondition("styleNumberId", EntityOperator.EQUALS, parameters.styleNumberId), true)
    if (!styleValue) return error("Style Number not found")

    return success([
        styleNumberId: styleValue.styleNumberId,
        styleNumber: styleValue.styleNumber,
        buyer: styleValue.buyer,
        description: styleValue.description,
        productType: styleValue.productType,
        productCategory: styleValue.productCategory,
        season: styleValue.season,
        status: styleValue.status
    ])
}

def createFtmStyleNumber(Map parameters) {
    def styleNumberId = org.apache.ofbiz.entity.util.EntitySeq.getNextSeqId("FtmStyleNumberSeq", delegator)
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
    delegator.create(styleMap)
    return success([styleNumberId: styleNumberId])
}

def updateFtmStyleNumber(Map parameters) {
    def styleValue = delegator.findOne("FtmStyleNumber", EntityCondition.makeCondition("styleNumberId", EntityOperator.EQUALS, parameters.styleNumberId), true)
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
        styleValue.set(updateMap)
        delegator.store(styleValue)
    }
    return success([styleNumberId: parameters.styleNumberId])
}

def deleteFtmStyleNumber(Map parameters) {
    def styleValue = delegator.findOne("FtmStyleNumber", EntityCondition.makeCondition("styleNumberId", EntityOperator.EQUALS, parameters.styleNumberId), true)
    if (!styleValue) return error("Style Number not found")

    delegator.delete(styleValue)
    return success([styleNumberId: parameters.styleNumberId])
}
