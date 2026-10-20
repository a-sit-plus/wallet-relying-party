// Minimal CBOR decoder (RFC 8949) for displaying COSE structures, e.g. the payload of a WRPRC CWT.
// Tags are dropped, maps with non-string keys are decoded with stringified keys.

export function decodeCbor(bytes) {
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
    let offset = 0

    const readLength = (info) => {
        if (info < 24) return info
        if (info === 24) return view.getUint8(offset++)
        if (info === 25) { const value = view.getUint16(offset); offset += 2; return value }
        if (info === 26) { const value = view.getUint32(offset); offset += 4; return value }
        if (info === 27) { const value = Number(view.getBigUint64(offset)); offset += 8; return value }
        throw new Error(`Unsupported CBOR length encoding ${info}`)
    }

    const readItem = () => {
        const initial = view.getUint8(offset++)
        const major = initial >> 5
        const info = initial & 0x1f
        if (info === 31) throw new Error('Indefinite-length CBOR items are not supported')
        switch (major) {
            case 0: return readLength(info)
            case 1: return -1 - readLength(info)
            case 2: {
                const length = readLength(info)
                const value = bytes.subarray(offset, offset + length)
                offset += length
                return value
            }
            case 3: {
                const length = readLength(info)
                const value = new TextDecoder().decode(bytes.subarray(offset, offset + length))
                offset += length
                return value
            }
            case 4: return Array.from({ length: readLength(info) }, readItem)
            case 5: {
                const length = readLength(info)
                const map = {}
                for (let i = 0; i < length; i++) {
                    const key = readItem()
                    map[String(key)] = readItem()
                }
                return map
            }
            case 6: readLength(info); return readItem()
            case 7:
                if (info === 20) return false
                if (info === 21) return true
                if (info === 22 || info === 23) return null
                if (info === 25) { const value = view.getFloat16 ? view.getFloat16(offset) : NaN; offset += 2; return value }
                if (info === 26) { const value = view.getFloat32(offset); offset += 4; return value }
                if (info === 27) { const value = view.getFloat64(offset); offset += 8; return value }
                throw new Error(`Unsupported CBOR simple value ${info}`)
        }
    }

    return readItem()
}

export function decodeBase64Url(value) {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized + '='.repeat((4 - normalized.length % 4) % 4)
    return Uint8Array.from(atob(padded), char => char.charCodeAt(0))
}

/** The decoded payload of a base64url-encoded COSE_Sign1, e.g. a CWT. */
export function decodeCoseSign1Payload(base64url) {
    const coseSign1 = decodeCbor(decodeBase64Url(base64url))
    if (!Array.isArray(coseSign1) || coseSign1.length !== 4 || !(coseSign1[2] instanceof Uint8Array)) {
        throw new Error('Not a COSE_Sign1 with attached payload')
    }
    return decodeCbor(coseSign1[2])
}
