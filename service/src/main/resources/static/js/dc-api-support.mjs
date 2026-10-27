// Protocol identifiers of the Digital Credentials API, as sent in navigator.credentials.get({ digital }).
export const DC_API_PROTOCOLS = {
    openId4VpSigned: 'openid4vp-v1-signed',
    openId4VpUnsigned: 'openid4vp-v1-unsigned',
    isoMdoc: 'org-iso-mdoc',
}

// Which Digital Credentials API protocols this browser offers: `true` or `false` where the browser
// answers DigitalCredential.userAgentAllowsProtocol, `null` where it can not tell (e.g. older Chrome versions).
// Safari, for instance, offers the API with org-iso-mdoc only.
export function dcApiSupport(scope = globalThis) {
    const digitalCredential = scope.DigitalCredential
    const available = typeof digitalCredential !== 'undefined'
    const allowsProtocol = available && typeof digitalCredential.userAgentAllowsProtocol === 'function'
        ? protocol => {
            try {
                return digitalCredential.userAgentAllowsProtocol(protocol) === true
            } catch {
                return null
            }
        }
        : () => null
    return {
        available,
        allows(protocol) {
            return available ? allowsProtocol(protocol) : false
        },
    }
}

// Whether an ISO 18013-7 Annex C (org-iso-mdoc) request can carry the selected credentials: ISO mdocs, and SD-JWT VCs
// as proposed by WG10 N2933. The device request lists the requested claims of an SD-JWT VC explicitly, so at least one
// has to be selected; it can not request all claims. Credentials are as selected in the request form, i.e. with
// `representation.value` and `attributes` that are `isSelected`.
export function isAnnexCRequest(credentials = []) {
    return credentials.length > 0 && credentials.every(credential => {
        switch (credential?.representation?.value) {
            case 'ISO_MDOC':
                return true
            case 'SD_JWT':
                return (credential.attributes || []).some(attribute => attribute.isSelected)
            default:
                return false
        }
    })
}
