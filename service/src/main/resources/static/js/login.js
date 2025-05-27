import {createApp, ref} from 'vue'

// --- STATIC --------------------------------------------------------

const STATIC_DEV = false

const URLs = {
    transactionUrl: STATIC_DEV ? 'api/transaction-create' : 'transaction/create',
    resultUrl: 'api/single/',
    logUrl: 'logs/',
    successPageUrl: 'customer-success.html?id=',
}

const createBasicSetup = function (config) {

    // --- STATE ---------------------------------------------------

    const reqSelection = ref({
        simple: false,
        presentationMechanismIdentifier: "presentation_definition",
        credentials: [],
    })
    const error = ref({message: null})
    const reqResult = ref({
        profiles: null,
    })
    const reqChanged = ref({
        oldRequestJSON: null,
        changed: false,
    })

    let resultInterval = null

    // --- FUNCTIONS -----------------------------------------------

    async function updateProfile(profile) {
        console.log('updateProfile', profile)

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
                sd: credential.sd,
                attributes: attrs
            })
        }

        reqSelection.value = {
            simple: profile.simple,
            credentials: credentials,
            profileLabel: profile.label,
            presentationMechanismIdentifier: "presentation_definition",
        }
        console.log('updateProfile result', reqSelection.value)

        compareRequestChanged()
    }

    async function updateSchemeType(credential, schemeType) {
        console.log('updateSchemeType', schemeType)
        credential.schemeType = schemeType
        credential.attributes = schemeType.attributes
        credential.validRepresentations = schemeType.validRepresentations
        credential.sd = schemeType.sd
        compareRequestChanged()
    }

    async function updatePresentationMechanismIdentifier(presentationMechanismIdentifier) {
        console.log('updatePresentationMechanismIdentifier', presentationMechanismIdentifier)
        reqSelection.value.presentationMechanismIdentifier = presentationMechanismIdentifier
        compareRequestChanged()
    }

    async function updateRepresentation(credential, representation) {
        console.log('updateRepresentation', representation)
        credential.representation = representation
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
            "sd": null,
            "attributes": []
        })
        compareRequestChanged()
    }

    async function removeCredential(credential) {
        console.log('removeCredential', credential)
        const index = reqSelection.value.credentials.indexOf(credential)
        reqSelection.value.credentials.splice(index, 1)
    }

    function validate() {
        const errors = [];

        // get allowed values
        const allowedSchemeTypes = config.schemeTypes.map(x => x.value)
        const allowedRepresentations = config.representation.map(x => x.value)

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
                attributes: credential.attributes.filter(x => x.isSelected).map(x => x.value),
            }
        })
        const request = {
            simple: reqSelection.value.simple,
            presentationMechanismIdentifier: reqSelection.value.presentationMechanismIdentifier,
            credentials: credentials,
        }

        return JSON.stringify(request)
    }

    function parseJwt(token) {
        const parts = token.split('.');

        if (parts.length !== 3) {
            throw new Error('Invalid JWT token');
        }

        // Source: https://stackoverflow.com/a/38552302
        const content = parts[1].replace(/-/g, '+').replace(/_/g, '/')
        const payload = decodeURIComponent(atob(content).split('').map(function (c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''))

        return JSON.parse(payload)
    }

    async function invokeDCAPI() {
        try {
            if (!document.getElementsByName("DCQL")[0].checked) {
                const confirmed = confirm("Presentation Mechanism will be set to DCQL for Digital Credentials API")
                if (!confirmed) {
                    return;
                }
                document.getElementsByName("DCQL")[0].click()
                await generateQrCode()
            }

            if (reqResult.value == null || reqResult.value.profiles == null)
                return;

            const urlString = reqResult.value.profiles[0].url
            const requestUri = new URLSearchParams(urlString).get("request_uri")

            const query = await fetch(requestUri)
                .then(async (response) => {
                    if (!response.ok) {
                        throw new Error('Network response was not ok ' + response.statusText);
                    }
                    const responseText = (await response.text())
                    const jwtResponse = parseJwt(responseText);

                    if (!('dcql_query' in jwtResponse)) {
                        throw new Error('Object does not have a query' + jwtResponse);
                    }

                    return {
                        dcql_query: jwtResponse.dcql_query,
                        nonce: jwtResponse.nonce
                    }
                });

            const requestData = {
                responseType: "vp_token",
                response_mode: "dc_api",
                nonce: query.nonce,
                dcql_query: query.dcql_query
            };

            const protocolName = "openid4vp"
            const providers = [{
                protocol: protocolName,
                request: JSON.stringify(requestData)
            }];

            const walletResponse = await navigator.credentials.get({
                digital: {
                    providers: providers,
                }
            });

            if (walletResponse.constructor.name == 'DigitalCredential') {
                const data = walletResponse.data
                const protocol = walletResponse.protocol
                console.log("Response Data: " + data + " Protocol: " + protocol)
            } else if (walletResponse.constructor.name == 'IdentityCredential') {
                const data = walletResponse.token
                console.log("Response Data: " + data)
            } else {
                throw new Error("Unknown response type")
            }
        } catch (err) {
            console.log('error: ', error)
        }
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
                    profiles: data.profiles
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
            if (reqResult.value == null || reqResult.value.profiles == null)
                return;
            for (const item of reqResult.value.profiles) {
                console.log('loadResult for ' + item.id);
                let response = await fetch(URLs.resultUrl + item.id);
                if (response.ok) {
                    const data = await response.json();
                    console.log('loadResult got: ', data);
                    // navigate to success page
                    window.location.href = URLs.successPageUrl + item.id
                }
                let logResponse = await fetch(URLs.logUrl + item.id);
                if (logResponse.ok) {
                    try {
                        const data = await logResponse.json();
                        data.forEach(it => console.log(it))
                    } catch (error) {
                        // do nothing
                    }
                }
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
        updatePresentationMechanismIdentifier,
        addCredential,
        removeCredential,
        updateAttribute,
        generateQrCode,
        startPeriodicUpdate,
        resetRequestChanged,
        compareRequestChanged,
        invokeDCAPI,
    }
}

export {createBasicSetup}
