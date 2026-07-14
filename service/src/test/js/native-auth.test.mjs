import assert from 'node:assert/strict'
import test from 'node:test'
import {nativeAuthConfiguration} from '../../main/resources/static/js/native-auth.mjs'

test('builds the fixed app callback for a valid iOS request', () => {
    const state = '123e4567-e89b-12d3-a456-426614174000'
    const configuration = nativeAuthConfiguration(`?client=ios&state=${state}`)

    assert.deepEqual(configuration.profileNames, ['MDOCISO'])
    assert.equal(
        configuration.callbackForTransaction('transaction-id'),
        `wallet-rp://auth/callback?transaction_id=transaction-id&state=${state}`,
    )
})

test('builds the same callback for a valid Android request', () => {
    const state = '123e4567-e89b-12d3-a456-426614174000'
    const configuration = nativeAuthConfiguration(`?client=android&state=${state}`)

    assert.deepEqual(configuration.profileNames, ['MDOCISO'])
    assert.equal(
        configuration.callbackForTransaction('transaction-id'),
        `wallet-rp://auth/callback?transaction_id=transaction-id&state=${state}`,
    )
})

test('leaves regular and malformed requests unchanged', () => {
    assert.equal(nativeAuthConfiguration(''), null)
    assert.equal(nativeAuthConfiguration('?client=ios&state=not-a-uuid'), null)
})
