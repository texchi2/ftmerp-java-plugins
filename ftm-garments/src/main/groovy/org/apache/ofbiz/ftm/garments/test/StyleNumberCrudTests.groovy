package org.apache.ofbiz.ftm.garments.test

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

/**
 * Oracle for the four FtmStyleNumber CRUD services (create, get, update, delete).
 * Each must run under OFBiz's GroovyEngine: create -> get -> update -> get -> delete,
 * with every step asserted, and an unknown id must return an error rather than crash.
 * This file is the fixed check; the services are what change to make it pass.
 */
class StyleNumberCrudTests extends OFBizTestCase {

    StyleNumberCrudTests(String name) {
        super(name)
    }

    void testCreateGetUpdateDelete() {
        Map created = dispatcher.runSync('createFtmStyleNumber', [userLogin: systemUser(),
                styleNumber: 'T1-ORACLE-01', buyer: 'ORACLE BUYER', productType: 'TOP',
                status: 'ACTIVE', season: 'SS27'])
        assert ServiceUtil.isSuccess(created)
        String id = created.styleNumberId
        assert id

        Map got = dispatcher.runSync('getFtmStyle', [styleNumberId: id])
        assert ServiceUtil.isSuccess(got)
        assert got.styleNumber == 'T1-ORACLE-01'
        assert got.buyer == 'ORACLE BUYER'
        assert got.season == 'SS27'

        Map updated = dispatcher.runSync('updateFtmStyleNumber', [userLogin: systemUser(),
                styleNumberId: id, buyer: 'NEW BUYER'])
        assert ServiceUtil.isSuccess(updated)
        Map again = dispatcher.runSync('getFtmStyle', [styleNumberId: id])
        assert ServiceUtil.isSuccess(again)
        assert again.buyer == 'NEW BUYER'
        assert again.styleNumber == 'T1-ORACLE-01'      // a field not sent stays as it was

        Map deleted = dispatcher.runSync('deleteFtmStyleNumber', [userLogin: systemUser(), styleNumberId: id])
        assert ServiceUtil.isSuccess(deleted)
        assert from('FtmStyleNumber').where('styleNumberId', id).queryOne() == null
    }

    void testUnknownIdIsAnErrorNotACrash() {
        Map got = dispatcher.runSync('getFtmStyle', [styleNumberId: 'T1-NO-SUCH-ID'])
        assert ServiceUtil.isError(got)
    }

    private GenericValue systemUser() {
        return from('UserLogin').where('userLoginId', 'system').queryOne()
    }

}
