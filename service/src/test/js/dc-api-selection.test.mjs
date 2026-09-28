import assert from 'node:assert/strict'
import test from 'node:test'
import {
    effectiveForgedSignerIds,
    effectiveSignerVerifierInfo,
    mismatchedVerifierInfoSignerIds,
    selectAllowMismatchedVerifierInfo,
    selectSignerVerifierInfo,
    effectiveSignerIds,
    selectForgedSigner,
    selectOid4vpMode,
    selectSigner,
    validateDcApiSelection,
} from '../../main/resources/static/js/dc-api-selection.mjs'

const profile = {
    dcApiSigners: [
        {id: 'dns-verifier'},
        {id: 'certificate-hash-verifier'},
    ],
}

test('switching from multisigned to signed keeps exactly one selected identity', () => {
    const selection = selectOid4vpMode(profile, {
        oid4vpMode: 'MULTISIGNED',
        signerIds: ['dns-verifier', 'certificate-hash-verifier'],
    }, 'SIGNED')

    assert.equal(selection.oid4vpMode, 'SIGNED')
    assert.deepEqual(effectiveSignerIds(selection), ['dns-verifier'])
    assert.deepEqual(validateDcApiSelection(selection, false), [])
})

test('switching signed to multisigned and back restores the signed identity', () => {
    const signed = selectSigner({
        oid4vpMode: 'SIGNED',
        signerIds: ['dns-verifier'],
    }, 'certificate-hash-verifier', true)
    const multisigned = selectOid4vpMode(profile, signed, 'MULTISIGNED')
    const restored = selectOid4vpMode(profile, multisigned, 'SIGNED')

    assert.deepEqual(multisigned.signerIds, ['certificate-hash-verifier', 'dns-verifier'])
    assert.deepEqual(restored.signerIds, ['certificate-hash-verifier'])
})

test('switching away from multisigned and back restores its identity selection', () => {
    const multisigned = selectSigner({
        oid4vpMode: 'MULTISIGNED',
        signerIds: ['dns-verifier', 'certificate-hash-verifier'],
    }, 'dns-verifier', false)
    const signed = selectOid4vpMode(profile, multisigned, 'SIGNED')
    const restored = selectOid4vpMode(profile, signed, 'MULTISIGNED')

    assert.deepEqual(restored.signerIds, ['certificate-hash-verifier'])
    assert.deepEqual(validateDcApiSelection(restored, false), [
        'Multisigned OpenID4VP requires at least two verifier identities.',
    ])
})

test('signed mode reports a missing verifier identity instead of selecting one implicitly', () => {
    const selection = {oid4vpMode: 'SIGNED', signerIds: []}

    assert.deepEqual(effectiveSignerIds(selection), [])
    assert.deepEqual(validateDcApiSelection(selection, false), [
        'Please select one verifier identity.',
    ])
})

test('any selected identity can be forged, including every one of a multisigned request', () => {
    let selection = {oid4vpMode: 'MULTISIGNED', signerIds: ['dns-verifier', 'certificate-hash-verifier']}
    selection = selectForgedSigner(selection, 'dns-verifier', true)
    selection = selectForgedSigner(selection, 'certificate-hash-verifier', true)

    assert.deepEqual(effectiveForgedSignerIds(selection), ['dns-verifier', 'certificate-hash-verifier'])
    assert.deepEqual(validateDcApiSelection(selection, false), [])
    assert.deepEqual(effectiveForgedSignerIds(selectForgedSigner(selection, 'dns-verifier', false)), [
        'certificate-hash-verifier',
    ])
})

test('the signed identity can be forged', () => {
    const selection = selectForgedSigner({oid4vpMode: 'SIGNED', signerIds: ['dns-verifier']}, 'dns-verifier', true)

    assert.deepEqual(effectiveForgedSignerIds(selection), ['dns-verifier'])
})

test('only selected identities of signed modes are sent as forged', () => {
    const selection = {
        oid4vpMode: 'MULTISIGNED',
        signerIds: ['dns-verifier', 'other-verifier'],
        forgedSignerIds: ['certificate-hash-verifier', 'dns-verifier'],
    }

    assert.deepEqual(effectiveForgedSignerIds(selection), ['dns-verifier'])
    assert.deepEqual(effectiveForgedSignerIds({...selection, oid4vpMode: 'UNSIGNED'}), [])
})

const signersWithWrpac = [
    {id: 'dns-verifier'},
    {id: 'wrpac', wrpac: true},
]

test('a registration certificate on the WRPAC signer is a regular combination', () => {
    const selection = selectSignerVerifierInfo({
        oid4vpMode: 'MULTISIGNED',
        signerIds: ['wrpac', 'dns-verifier'],
    }, 'wrpac', 2)

    assert.deepEqual(effectiveSignerVerifierInfo(selection), [['wrpac', 2]])
    assert.deepEqual(mismatchedVerifierInfoSignerIds(selection, signersWithWrpac), [])
    assert.deepEqual(validateDcApiSelection(selection, false, signersWithWrpac), [])
})

test('a registration certificate on another signer needs the mismatch test option', () => {
    const mismatched = selectSignerVerifierInfo({
        oid4vpMode: 'MULTISIGNED',
        signerIds: ['wrpac', 'dns-verifier'],
    }, 'dns-verifier', 0)

    assert.deepEqual(mismatchedVerifierInfoSignerIds(mismatched, signersWithWrpac), ['dns-verifier'])
    assert.equal(validateDcApiSelection(mismatched, false, signersWithWrpac).length, 1)
    assert.deepEqual(
        validateDcApiSelection(selectAllowMismatchedVerifierInfo(mismatched, true), false, signersWithWrpac),
        [],
    )
})

test('explicitly no registration certificate is sent, unselected signers are not', () => {
    let selection = {oid4vpMode: 'SIGNED', signerIds: ['wrpac']}
    selection = selectSignerVerifierInfo(selection, 'wrpac', null)
    selection = selectSignerVerifierInfo(selection, 'dns-verifier', 1)

    assert.deepEqual(effectiveSignerVerifierInfo(selection), [['wrpac', null]])
    assert.deepEqual(effectiveSignerVerifierInfo({...selection, oid4vpMode: 'UNSIGNED'}), [])
})
