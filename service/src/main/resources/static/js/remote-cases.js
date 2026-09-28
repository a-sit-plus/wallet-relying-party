// EUDI.Wallet Launchpad 2026 Testing Playbook, section 2 (Remote presentation).
// A case describes the request and the result to inspect; wallet issuance and LoTE setup
// are preconditions performed outside this relying-party service.
const pidSd = {schemeType: 'urn:eudi:pid:1', representation: 'SD_JWT'}
const pidMdoc = {schemeType: 'eu.europa.ec.eudi.pid.1', representation: 'ISO_MDOC'}
const mdl = {schemeType: 'org.iso.18013.5.1.mDL', representation: 'ISO_MDOC'}

const flow = {
    redirect: {label: 'OpenID4VP redirect (eu-eaap://)', profileName: 'EUDIW2026', selection: null},
    annex: {label: 'ISO 18013-7 Annex C', profileName: 'EUDIW2026', selection: {oid4vpMode: 'NONE', isoMdoc: true, encrypt: true}},
    dcapi: {label: 'OpenID4VP via Digital Credentials API', profileName: 'EUDIW2026', selection: {oid4vpMode: 'SIGNED', isoMdoc: false, encrypt: true}},
}

function presentation(number, title, flowKey, credentials, note = '') {
    return {
        id: number, title, flow: flowKey, credentials,
        description: `Present ${title.toLowerCase()} to the remote verifier using ${flow[flowKey].label}.`,
        precondition: 'The wallet contains the requested credential(s). The verifier trusts the issuer anchor from the LoTE, and the wallet trusts the reader WRPAC and WRPRC.',
        expected: 'The wallet asks for consent. After sharing, the reader displays the credential data.',
        note,
    }
}

export const remoteCases = [
    presentation(1, 'PID in SD-JWT format', 'redirect', [pidSd]),
    presentation(2, 'PID in mdoc format', 'redirect', [pidMdoc]),
    presentation(3, 'mDL in mdoc format', 'redirect', [mdl]),
    presentation(4, 'Combined PID and mDL', 'redirect', [pidMdoc, pidSd, mdl],
        'Combine only credentials that worked individually. Remove any unavailable credential in Request Details.'),
    presentation(5, 'PID in SD-JWT format', 'annex', [pidSd],
        'Optional playbook case. This service cannot encode SD-JWT VC in the ISO device request/response; the Annex C action is unavailable.'),
    presentation(6, 'PID in mdoc format', 'annex', [pidMdoc]),
    presentation(7, 'mDL in mdoc format', 'annex', [mdl]),
    presentation(8, 'Combined PID and mDL in mdoc format', 'annex', [pidMdoc, mdl],
        'Annex C conversion currently supports mdoc requests. Combine only credentials that worked individually.'),
    presentation(9, 'PID in SD-JWT format', 'dcapi', [pidSd]),
    presentation(10, 'PID in mdoc format', 'dcapi', [pidMdoc]),
    presentation(11, 'mDL in mdoc format', 'dcapi', [mdl]),
    presentation(12, 'Combined PID and mDL', 'dcapi', [pidMdoc, pidSd, mdl],
        'Combine only credentials that worked individually. Remove any unavailable credential in Request Details.'),
    ...[
        ['redirect', 'OpenID4VP redirect'],
        ['annex', 'ISO 18013-7 Annex C'],
        ['dcapi', 'OpenID4VP via Digital Credentials API'],
    ].map(([flowKey, label], offset) => ({
        id: 13 + offset, title: `Untrusted reader certificate: ${label}`, flow: flowKey,
        credentials: [pidMdoc],
        description: 'Check that the wallet warns when the reader trust anchor is not trusted.',
        precondition: 'Use a credential that worked in a happy flow. Configure the wallet with the playbook’s empty WRPAC LoTE. Select the reader certificate in Request Details.',
        expected: 'The wallet warns that the reader is not trusted. The case passes if sharing stops or the user chooses to continue.',
        note: 'This service cannot change the wallet’s LoTE; configure it in the wallet before starting.',
    })),
    ...[
        {id: 16, credentials: [pidMdoc], title: 'Untrusted mdoc issuer anchor'},
        {id: 17, credentials: [pidSd], title: 'Untrusted SD-JWT issuer anchor'},
    ].map(item => ({
        ...item, flow: 'redirect', emptyIssuerTrustList: true,
        description: 'Present a valid credential while the reader evaluates issuer trust against an empty issuer trust list.',
        precondition: 'First confirm the credential works in a happy flow. The wallet trusts the reader certificates.',
        expected: 'The reader reports the PID/mDL provider anchor as untrusted. Credential data may still be shown.',
        note: 'The empty trust list applies only to this transaction’s displayed issuer trust result.',
    })),
    {
        id: 18, title: 'Revoked mdoc: MSO identifier list', flow: 'redirect', credentials: [pidMdoc],
        description: 'Present an mdoc whose MSO identifier is on its referenced identifier list.',
        precondition: 'Issue and revoke a PID/mDL with the ITB. The reader trusts the issuer and revocation-list signer.',
        expected: 'The reader warns that the document is on the identifier list. Data display is optional.',
        note: 'Revocation comes from the credential and its referenced list; this page does not manufacture a revoked credential.',
    },
    {
        id: 19, title: 'Revoked mdoc: CWT status list', flow: 'redirect', credentials: [pidMdoc],
        description: 'Present an mdoc whose bit is set on its referenced CWT status list.',
        precondition: 'Issue and revoke an mdoc with the ITB. The reader trusts the issuer and status-list signer.',
        expected: 'The reader warns that the document bit is set on the status list. Data display is optional.',
        note: 'Revocation comes from the credential and its referenced list; this page does not manufacture a revoked credential.',
    },
    {
        id: 20, title: 'Revoked SD-JWT: JWT status list', flow: 'redirect', credentials: [pidSd],
        description: 'Present an SD-JWT VC whose bit is set on its referenced JWT status list.',
        precondition: 'Issue and revoke a PID SD-JWT with the ITB. The reader trusts the issuer and status-list signer.',
        expected: 'The reader warns that the credential bit is set on the status list. Data display is optional.',
        note: 'Revocation comes from the credential and its referenced list; this page does not manufacture a revoked credential.',
    },
    {
        id: 21, title: 'Remote qualified electronic signature of a PDF', flow: 'qes', credentials: [],
        description: 'Use an OpenID4VP redirect flow to authorize signing a PDF with a qualified signature.',
        precondition: 'A wallet, signing service, and PDF document are available.',
        expected: 'The wallet shows the PDF for authorization and the reader shows the signed PDF.',
        note: 'The external signature service performs the document-signing flow.',
        externalUrl: 'https://apps.egiz.gv.at/drivingapp/',
    },
]

export function caseById(id) {
    return remoteCases.find(item => item.id === Number(id))
}

export function caseFlow(item) {
    return flow[item.flow] || null
}

