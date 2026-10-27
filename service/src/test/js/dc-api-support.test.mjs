import assert from 'node:assert/strict'
import test from 'node:test'
import {DC_API_PROTOCOLS, dcApiSupport, isAnnexCRequest} from '../../main/resources/static/js/dc-api-support.mjs'

test('reports no protocol without the Digital Credentials API', () => {
    const support = dcApiSupport({})

    assert.equal(support.available, false)
    assert.equal(support.allows(DC_API_PROTOCOLS.isoMdoc), false)
})

test('asks the browser which protocols it allows', () => {
    const support = dcApiSupport({
        DigitalCredential: {userAgentAllowsProtocol: protocol => protocol === DC_API_PROTOCOLS.isoMdoc},
    })

    assert.equal(support.available, true)
    assert.equal(support.allows(DC_API_PROTOCOLS.isoMdoc), true)
    assert.equal(support.allows(DC_API_PROTOCOLS.openId4VpSigned), false)
})

test('can not tell when the browser does not answer for protocols', () => {
    const support = dcApiSupport({DigitalCredential: function DigitalCredential() {}})

    assert.equal(support.available, true)
    assert.equal(support.allows(DC_API_PROTOCOLS.openId4VpSigned), null)
})

test('can not tell when the browser throws for a protocol', () => {
    const support = dcApiSupport({
        DigitalCredential: {userAgentAllowsProtocol: () => { throw new TypeError('unknown') }},
    })

    assert.equal(support.allows(DC_API_PROTOCOLS.openId4VpUnsigned), null)
})

function credential(representation, ...selected) {
    return {representation: {value: representation}, attributes: selected.map(isSelected => ({isSelected}))}
}

test('ISO Annex C carries mdocs, and SD-JWT VCs with selected claims', () => {
    assert.equal(isAnnexCRequest([credential('ISO_MDOC')]), true)
    assert.equal(isAnnexCRequest([credential('SD_JWT', false, true)]), true)
    assert.equal(isAnnexCRequest([credential('ISO_MDOC'), credential('SD_JWT', true)]), true)
})

test('ISO Annex C can not request all claims of an SD-JWT VC, other formats, or nothing', () => {
    assert.equal(isAnnexCRequest([credential('SD_JWT', false)]), false)
    assert.equal(isAnnexCRequest([credential('SD_JWT')]), false)
    assert.equal(isAnnexCRequest([credential('ISO_MDOC'), credential('PLAIN_JWT', true)]), false)
    assert.equal(isAnnexCRequest([]), false)
})
