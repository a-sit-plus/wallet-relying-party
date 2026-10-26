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
