import {createApp, ref} from 'vue'

// --- STATIC --------------------------------------------------------

const STATIC_DEV = false

const URLs = {
    loginConfigUrl: 'js/login-config.json',
    transactionUrl: STATIC_DEV ? 'api/transaction-create' : 'transaction/create',
    resultUrl: 'api/single/',
    successPageUrl: 'customer-success.html?id=',
}

const loadConfig = async function() {
    // load from json
    const response = await fetch(URLs.loginConfigUrl)
    const config = await response.json()
    //console.log(config)

    return config
}

const createBasicSetup = function(config) {

    // --- STATE ---------------------------------------------------

    const reqSelection = ref({
        urlprefix: null,
        credentials: [],
    })
    const error = ref({message: null})
    const reqResult = ref({
        id: null,
        qrCodeSrc: null,
        linkSrc: null,
    })
    const reqChanged = ref({
        oldRequestJSON: null,
        changed: false,
    })

    let resultInterval = null

    // --- FUNCTIONS -----------------------------------------------

    async function updateProfile(profile) {
        console.log('updateProfile', profile)

        const urlprefix = config.urlprefix.find((up) => up.value == profile.urlprefix)

        let credentials = []
        for (let key in profile.credentials) {
            let credential = profile.credentials[key]

            // for value in profile, get expanded label/value form from config
            const schemeType = config.schemeTypes.find((type) => type.value == credential.schemeType)
            const representation = config.representation.find((type) => type.value == credential.representation)

            let attrs = []
            if (schemeType) {
                // find attributes that are marked as isSelected in profile
                const selected = credential.attributes.filter(x => x.isSelected).map(x => x.value)

                // take all attribtues from config for this credential type, and mark some as isSelected
                schemeType.attributes.forEach(attr => attr.isSelected = selected.includes(attr.value))
                attrs = schemeType.attributes
            }

            credentials.push({
                schemeType: schemeType,
                representation: representation,
                attributes: attrs
            })
        }

        reqSelection.value = {
            urlprefix: urlprefix,
            credentials: credentials,
            profileLabel: profile.label,
        }
        console.log('updateProfile result', reqSelection.value)

        compareRequestChanged()
    }

    async function updateSchemeType(credential, schemeType) {
        console.log('updateSchemeType', schemeType)
        credential.schemeType = schemeType
        credential.attributes = schemeType.attributes
        compareRequestChanged()
    }

    async function updateRepresentation(credential, representation) {
        console.log('updateRepresentation', representation)
        credential.representation = representation
        compareRequestChanged()
    }

    async function updateUrlprefix(urlprefix) {
        console.log('updateUrlprefix', urlprefix)
        reqSelection.value.urlprefix = urlprefix
        compareRequestChanged()
    }

    async function updateAttribute(attribute) {
        console.log('updateAttribute', attribute)
        attribute.isSelected = !attribute.isSelected
        compareRequestChanged()
    }

    async function addCredential() {
        console.log('addCredential')
        reqSelection.value.credentials.push({
            "schemeType": null,
            "representation": null,
            "attributes": []
        })
        compareRequestChanged()
    }

    async function removeCredential(credential) {
        console.log('removeCredential', credential)
        const index = reqSelection.value.credentials.indexOf(credential)
        reqSelection.value.credentials.splice(index, 1)
    }

    function validate () {
        const errors = []

        // get allowed values
        const allowedPrefixes = config.urlprefix.map(x => x.value)
        const allowedSchemeTypes = config.schemeTypes.map(x => x.value)
        const allowedRepresentations = config.representation.map(x => x.value)

        // check url prefix
        const prefix = reqSelection.value.urlprefix
        if (prefix == null || typeof prefix != "object" || typeof prefix.value != "string")
            errors.push("URL Prefix not set")
        else if (!allowedPrefixes.includes(prefix.value))
            errors.push("URL Prefix invalid")

        // check credentials
        for (let index in reqSelection.value.credentials) {
            const credential = reqSelection.value.credentials[index]

            // check scheme type
            const schemeType = credential.schemeType
            if (schemeType == null || typeof schemeType != "object" || typeof schemeType.value != "string")
                errors.push("Credential Type not set")
            else if (!allowedSchemeTypes.includes(schemeType.value))
                errors.push("Credential Type invalid")

            // check representation type
            const representation = credential.representation
            if (representation == null || typeof representation != "object" || typeof representation.value != "string")
                errors.push("Presentation Type not set")
            else if (!allowedRepresentations.includes(representation.value))
                errors.push("Presentation Type invalid")
        }

        return errors
    }

    function createRequestJSON() {
        // build request payload
        // from form inputs, take values and only selected attributes
        const credentials = reqSelection.value.credentials.map(credential => {
            return {
                credentialType: credential.schemeType.value,
                representation: credential.representation.value,
                attributes: credential.attributes.filter(x => x.isSelected).map(x => x.valuse),
            }
        })
        const request = {
            urlprefix: reqSelection.value.urlprefix.value,
            credentials: credentials,
        }

        return JSON.stringify(request)
    }

    async function generateQrCode() {
        console.log('generateQrCode', reqSelection.value)

        try {
            const validationErrors = validate()
            if (validationErrors.length > 0) {
                reqResult.value = null
                error.value.message = "Validation Error: " + validationErrors.join(", ")
                return
            }

            const requestJSON = createRequestJSON()
            console.log(`generateQrCode: fetching ${requestJSON}`)

            // request QR code, link url and id
            const response = await fetch(URLs.transactionUrl, {
                method: STATIC_DEV ? 'GET' : 'POST',
                headers: {"Content-Type": "application/json"},
                body: STATIC_DEV ? null : requestJSON,
            })
            if (response.ok) {
                const data = await response.json()
                console.log(`generateQrCode: got ${data}`)
                reqResult.value = {
                    qrCodeSrc: "data:image/png;base64," + data.qrCodePng,
                    linkSrc: data.qrCodeUrl,
                    id: data.id,
                }
                error.value.message = null
                resetRequestChanged()
            } else {
                const data = (await response.text())
                console.log(`generateQrCode: error ${data}`)
                throw data
            }
        } catch (err) {
            console.log(`error: ${err}`)
            reqResult.value = null
            error.value.message = "Error generating request: " + err
        }
    }

    async function loadResult() {
        try {
            if (reqResult.value == null || reqResult.value.id == null)
                return;
            console.log('loadResult for ' + reqResult.value.id)
            let response = await fetch(URLs.resultUrl + reqResult.value.id)
            const data = await response.json()
            console.log('loadResult got: ', data)
            if (response.ok) {
                // navigate to success page
                window.location.href = URLs.successPageUrl + reqResult.value.id
            }
        } catch (error) {
            console.log('error: ', error)
        }
    }

    async function startPeriodicUpdate() {
        if (resultInterval == null) {
            loadResult() // initially
            resultInterval = setInterval(loadResult, 2000) // periodically
        }
    }

    function resetRequestChanged() {
        try {
            const requestJSON = createRequestJSON()
            const changed = requestJSON != reqChanged.value.oldRequestJSON
            reqChanged.value.changed = false
            reqChanged.value.oldRequestJSON = requestJSON
        } catch (error) {
            reqChanged.value.changed = false
        }
    }

    function compareRequestChanged() {
        try {
            const requestJSON = createRequestJSON()
            const changed = requestJSON != reqChanged.value.oldRequestJSON
            reqChanged.value.changed = changed
        } catch (error) {
            reqChanged.value.changed = true
        }
    }

    updateProfile(config.profiles[0])

    // --- RETURNS -------------------------------------------------

    return {
        config,
        reqSelection,
        reqResult,
        error,
        reqChanged,
        updateProfile,
        updateSchemeType,
        updateRepresentation,
        updateUrlprefix,
        addCredential,
        removeCredential,
        updateAttribute,
        generateQrCode,
        startPeriodicUpdate,
        resetRequestChanged,
        compareRequestChanged,
    }
}

export { loadConfig, createBasicSetup }
