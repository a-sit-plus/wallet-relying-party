export function effectiveSignerIds(selection) {
    return Array.isArray(selection?.signerIds) ? selection.signerIds : []
}

export function selectOid4vpMode(profile, selection, mode) {
    const availableSignerIds = (profile?.dcApiSigners || []).map(signer => signer.id)
    const currentSignerIds = effectiveSignerIds(selection).filter(id => availableSignerIds.includes(id))
    const nextSelection = {...selection}
    if (selection?.oid4vpMode === 'SIGNED' && currentSignerIds.length === 1) {
        nextSelection.signedSignerId = currentSignerIds[0]
    } else if (selection?.oid4vpMode === 'MULTISIGNED') {
        nextSelection.multisignedSignerIds = currentSignerIds
    }

    if (mode === 'SIGNED') {
        const signerId = availableSignerIds.includes(nextSelection.signedSignerId)
            ? nextSelection.signedSignerId
            : currentSignerIds[0] || availableSignerIds[0]
        nextSelection.signerIds = [signerId].filter(Boolean)
        nextSelection.signedSignerId = signerId
    } else if (mode === 'MULTISIGNED') {
        const rememberedSignerIds = Array.isArray(nextSelection.multisignedSignerIds)
            ? nextSelection.multisignedSignerIds.filter(id => availableSignerIds.includes(id))
            : null
        nextSelection.signerIds = rememberedSignerIds ?? [
            ...currentSignerIds,
            ...availableSignerIds.filter(id => !currentSignerIds.includes(id)),
        ].slice(0, 2)
        nextSelection.multisignedSignerIds = nextSelection.signerIds
    }
    nextSelection.oid4vpMode = mode
    return nextSelection
}

export function selectSigner(selection, signerId, checked) {
    if (selection?.oid4vpMode === 'SIGNED') {
        return {...selection, signerIds: [signerId], signedSignerId: signerId}
    }
    const selected = effectiveSignerIds(selection)
    const signerIds = checked
        ? [...new Set([...selected, signerId])]
        : selected.filter(id => id !== signerId)
    return {...selection, signerIds, multisignedSignerIds: signerIds}
}

// Test option: selected identities whose signature the relying party deliberately breaks.
export function effectiveForgedSignerIds(selection) {
    if (selection?.oid4vpMode !== 'SIGNED' && selection?.oid4vpMode !== 'MULTISIGNED') return []
    const forged = Array.isArray(selection?.forgedSignerIds) ? selection.forgedSignerIds : []
    return effectiveSignerIds(selection).filter(id => forged.includes(id))
}

export function selectForgedSigner(selection, signerId, forged) {
    const current = Array.isArray(selection?.forgedSignerIds) ? selection.forgedSignerIds : []
    const forgedSignerIds = forged
        ? [...new Set([...current, signerId])]
        : current.filter(id => id !== signerId)
    return {...selection, forgedSignerIds}
}

// Registration certificates are issued to the WRPAC's relying party, so a WRPAC signer carries one by default: the
// preferred one, e.g. the transaction's selected WRPRC, otherwise the first available one. Others carry none.
export function defaultSignerWrprcId(signer, registrationCertificates, preferredWrprcId = null) {
    if (!signer?.wrpac) return null
    const ids = (registrationCertificates || []).map(certificate => certificate.id)
    if (preferredWrprcId != null && ids.includes(preferredWrprcId)) return preferredWrprcId
    return ids[0] ?? null
}

// Registration certificate of a signer: the one chosen for it, which may be null for none, otherwise its default.
export function signerWrprcId(selection, signer, registrationCertificates) {
    const verifierInfo = selection?.signerVerifierInfo || {}
    if (Object.prototype.hasOwnProperty.call(verifierInfo, signer.id)) return verifierInfo[signer.id]
    return defaultSignerWrprcId(signer, registrationCertificates, selection?.defaultWrprcId ?? null)
}

// Registration certificate (WRPRC) per selected signer: an id, or null for none. Given the signers and the available
// registration certificates, WRPAC signers without an entry get their default, see defaultSignerWrprcId. Signers
// still without an entry use the server default, which is the transaction's selected WRPRC for the WRPAC signer and
// none for any other.
export function effectiveSignerVerifierInfo(selection, signers = [], registrationCertificates = []) {
    if (selection?.oid4vpMode !== 'SIGNED' && selection?.oid4vpMode !== 'MULTISIGNED') return []
    const verifierInfo = selection?.signerVerifierInfo || {}
    return effectiveSignerIds(selection).flatMap(id => {
        if (Object.prototype.hasOwnProperty.call(verifierInfo, id)) return [[id, verifierInfo[id]]]
        const signer = (signers || []).find(candidate => candidate.id === id)
        const wrprcId = signer ? signerWrprcId(selection, signer, registrationCertificates) : null
        return wrprcId == null ? [] : [[id, wrprcId]]
    })
}

export function selectSignerVerifierInfo(selection, signerId, wrprcId) {
    return {
        ...selection,
        signerVerifierInfo: {...(selection?.signerVerifierInfo || {}), [signerId]: wrprcId ?? null},
    }
}

// Test option: registration certificates are issued to the WRPAC's relying party, so attaching one to any other
// signer is a mismatch that wallets should reject.
export function selectAllowMismatchedVerifierInfo(selection, allowed) {
    return {...selection, allowMismatchedVerifierInfo: allowed === true}
}

export function mismatchedVerifierInfoSignerIds(selection, signers) {
    const wrpacIds = (signers || []).filter(signer => signer.wrpac).map(signer => signer.id)
    return effectiveSignerVerifierInfo(selection)
        .filter(([id, wrprcId]) => wrprcId != null && !wrpacIds.includes(id))
        .map(([id]) => id)
}

export function validateDcApiSelection(selection, isoMdocRequest, signers = []) {
    const errors = []
    const signerIds = effectiveSignerIds(selection)
    const isoSelected = selection?.isoMdoc === true && isoMdocRequest
    if (!selection || !['NONE', 'SIGNED', 'MULTISIGNED', 'UNSIGNED'].includes(selection.oid4vpMode)) {
        errors.push('Please choose an OpenID4VP mode for the Digital Credentials API request.')
    } else if (selection.oid4vpMode === 'NONE' && !isoSelected) {
        errors.push('Please select at least one Digital Credentials API request type.')
    } else if (selection.oid4vpMode === 'SIGNED' && signerIds.length === 0) {
        errors.push('Please select one verifier identity.')
    } else if (selection.oid4vpMode === 'SIGNED' && signerIds.length !== 1) {
        errors.push('Signed OpenID4VP requires exactly one verifier identity.')
    } else if (selection.oid4vpMode === 'MULTISIGNED' && signerIds.length < 2) {
        errors.push('Multisigned OpenID4VP requires at least two verifier identities.')
    } else if (selection.allowMismatchedVerifierInfo !== true
        && mismatchedVerifierInfoSignerIds(selection, signers).length > 0) {
        errors.push('Registration certificates belong to the WRPAC identity. '
            + 'Allow mismatched combinations to attach one to another identity.')
    }
    return errors
}
