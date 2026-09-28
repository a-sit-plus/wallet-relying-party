import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('../../main/resources/static/js/remote-cases.js', import.meta.url), 'utf8')
const {remoteCases, caseFlow} = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'))

test('every 2026 remote presentation case is listed with setup and expected result', () => {
    assert.deepEqual(remoteCases.map(item => item.id), Array.from({length: 21}, (_, i) => i + 1))
    for (const item of remoteCases) {
        assert.ok(item.title)
        assert.ok(item.description)
        assert.ok(item.precondition)
        assert.ok(item.expected)
    }
})

test('transport groups select the requested protocol', () => {
    assert.deepEqual(remoteCases.slice(0, 4).map(item => item.flow), Array(4).fill('redirect'))
    assert.deepEqual(remoteCases.slice(4, 8).map(item => item.flow), Array(4).fill('annex'))
    assert.deepEqual(remoteCases.slice(8, 12).map(item => item.flow), Array(4).fill('dcapi'))
    assert.deepEqual(caseFlow(remoteCases[5]).selection, {oid4vpMode: 'NONE', isoMdoc: true, encrypt: true})
    assert.deepEqual(caseFlow(remoteCases[8]).selection, {oid4vpMode: 'SIGNED', isoMdoc: false, encrypt: true})
})
