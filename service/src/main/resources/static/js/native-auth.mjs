const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function nativeAuthConfiguration(search) {
    const parameters = new URLSearchParams(search)
    const state = parameters.get('state')

    if (!['android', 'ios'].includes(parameters.get('client')) || !UUID_PATTERN.test(state || '')) {
        return null
    }

    return {
        profileNames: ['MDOCISO'],
        callbackForTransaction(transactionId) {
            const callback = new URL('wallet-rp://auth/callback')
            callback.searchParams.set('transaction_id', transactionId)
            callback.searchParams.set('state', state)
            return callback.toString()
        },
    }
}
