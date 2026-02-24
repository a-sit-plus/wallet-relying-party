import {createApp, ref, watch, computed, nextTick} from 'vue'

// --- STATIC --------------------------------------------------------

const STATIC_DEV = false

const URLs = {
    transactionUrl: STATIC_DEV ? 'api/transaction-create' : 'transaction/create',
    resultUrl: 'api/single/',
    buildCredentialQueriesUrl: 'utilities/buildCredentialQueries',
    logUrl: 'logs/',
    successPageUrl: 'customer-success.html?id=',
    postUrl: 'transaction/result/'
}

const createBasicSetup = function (config) {

    // --- STATE ---------------------------------------------------

    const reqSelection = ref({
        presentationMechanismIdentifier: "dcql_query",
        credentials: [],
        presentationDefinition: null,
        presentationDefinitionError: null,
        dcqlQuery: null,
        dcqlQueryError: null,
        deviceRequest: null,
        deviceRequestError: null,
        includeWrpac: false,
        includeWrprc: false,
    })
    const selections = ref({})
    const credentialRequestOptions = ref({})
    const error = ref({type: null, message: null})
    const reqResult = ref({
        profiles: null,
    })
    const activeProfileName = ref(null)
    const activeProfile = computed(() => {
        if (!reqResult.value?.profiles || !activeProfileName.value) {
            return null
        }
        return reqResult.value.profiles.find(profile => profile.name === activeProfileName.value) || null
    })
    const reqChanged = ref({
        oldRequestJSON: null,
        changed: false,
    })
    const requestedCredentialsChanged = ref({
        oldRequestedCredentialsJSON: null,
        changed: false,
    })
    const certOptions = ref([])
    const certPreviews = ref([])
    const registrationState = ref({
        status: "unregistered",
        wrpIdentifier: null,
        serviceUri: null,
        hasWrpac: false,
        hasWrprc: false,
        wrpRegistration: {
            displayName: "A-SIT EUDI Relying Party",
            tradeName: "A-SIT EUDI Relying Party",
            country: "AT",
            isPsb: false,
            isIntermediary: false,
            supportUri: "http://localhost:8080/",
            privacyPolicyUri: "http://localhost:8080/privacy",
            entitlement: ["urn:eudi:entitlement:age-verification"],
            providerType: 5,
            supervisoryAuthorityIdentifierType: "NATIONAL",
            supervisoryAuthorityIdentifier: "",
        },
        serviceRegistration: {
            serviceName: "Terminal Service Point",
            serviceUri: "http://localhost:8080/custom.html",
            purpose: "Age verification 18+",
            credentials: [
                {
                    credentialType: "AgeVerification",
                    format: "vc+sd-jwt",
                    claims: ["/age_over_18"],
                }
            ],
        },
    })
    const registrationEndpointBaseUrl = ref(
        (typeof window !== "undefined" && window.localStorage?.getItem("registrationEndpointBaseUrl")) || "http://localhost:5000"
    )
    const registrarEmail = ref((() => {
        if (typeof window === "undefined") return "asit-demo@wrp.test"
        const stored = window.localStorage?.getItem("registrarEmail")
        if (!stored || !stored.trim()) return "asit-demo@wrp.test"
        if (stored.trim().toLowerCase() === "demo@wrp.test") {
            try {
                window.localStorage?.setItem("registrarEmail", "asit-demo@wrp.test")
            } catch (_ignored) {
                // ignore persistence errors in restricted browser modes
            }
            return "asit-demo@wrp.test"
        }
        return stored
    })())
    const registrarPassword = ref(
        (typeof window !== "undefined" && window.localStorage?.getItem("registrarPassword")) || "demo123"
    )

    let resultInterval = null
    let requestQueriesPromise = null

    // --- FUNCTIONS -----------------------------------------------

    function createRequestBuilderJSON() {
        // build request payload
        // from form inputs, take values and only selected attributes
        const credentials = reqSelection.value.credentials.map(credential => {
            return {
                credentialType: credential.schemeType.value,
                representation: credential.representation.value,
                attributes: credential.attributes.filter(x => x.isSelected).map(x => x.value),
            }
        })

        return JSON.stringify(credentials)
    }

    async function buildCredentialQueries(requestBuilderJson) {
        const response = await fetch(URLs.buildCredentialQueriesUrl, {
            method: "POST",
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
            },
            body: requestBuilderJson
        });
        if (!response.ok) {
            const errorMessage = await parseErrorResponse(response)
            throw new Error('Network response was not ok: ' + errorMessage);
        }
        let queries = await response.json();

        return {
            presentationDefinition: JSON.stringify(queries["presentationDefinition"], null, 4),
            presentationDefinitionError: queries["presentationDefinitionError"],
            dcqlQuery: JSON.stringify(queries["dcqlQuery"], null, 4),
            dcqlQueryError: queries["dcqlQueryError"],
            deviceRequest: queries["deviceRequest"],
            deviceRequestError: queries["deviceRequestError"],
        }
    }

    function clearError() {
        error.value.type = null
        error.value.message = null
    }

    function setError(type, message = null) {
        error.value.type = type
        error.value.message = message
    }

    async function parseErrorResponse(response) {
        const contentType = response.headers.get('content-type') || ''
        let errorBody = "Unknown error"

        try {
            if (contentType.includes('json')) {
                errorBody = await response.json()
            } else {
                errorBody = await response.text()
            }
        } catch (parseError) {
            console.warn('Failed to parse error response', parseError)
        }

        if (typeof errorBody === 'string' && errorBody.trim()) {
            return errorBody
        }
        if (errorBody && typeof errorBody === 'object') {
            return errorBody.detail || errorBody.error || errorBody.message || JSON.stringify(errorBody)
        }
        return response.statusText
    }

    function validateDcApiSelection(selection) {
        const errors = [];
        if (!selection || typeof selection.signedOid4vp !== 'boolean') {
            errors.push("Please select an OpenID4VP mode for the Digital Credentials API request.");
        }
        return errors;
    }

    async function prefetchCredentialRequestOptions(profile, selection) {
        console.log('prefetchCredentialRequestOptions for profile', profile.name, 'with selection', selection)
        if (!profile || !profile.url) {
            if (profile) credentialRequestOptions.value[profile.name] = null
            return
        }

        try {
            // clear previous error
            clearError()
            const requestUrl = new URL(profile.dcApiUrl || profile.url)

            if (!selection || typeof selection.signedOid4vp !== 'boolean') {
                credentialRequestOptions.value[profile.name] = null
                return
            }

            const validationErrors = validateDcApiSelection(selection);
            if (validationErrors.length > 0) {
                credentialRequestOptions.value[profile.name] = null;
                setError("GENERIC", validationErrors.join(" "));
                return;
            }

            requestUrl.searchParams.set('dcApiSignedOid4vp', String(selection.signedOid4vp))
            console.log("Going to pre-fetch from requestUri", requestUrl.toString())

            const response = await fetch(requestUrl);
            if (!response.ok) {
                const errorMessage = await parseErrorResponse(response)

                if (response.status === 400 && typeof errorMessage === 'string' && errorMessage.startsWith("Wrong representation")) {
                    setError("INCOMPATIBLE_PRESENTATION_TYPE");
                    credentialRequestOptions.value[profile.name] = null;
                    return;
                } else {
                    throw new Error('Network response was not ok: ' + errorMessage);
                }
            }

            const options = await response.json();

            credentialRequestOptions.value[profile.name] = options
            console.log('pre-fetched options for profile', profile.name, options)
        } catch (err) {
            console.log('error in pre-fetch: ', err)
            setError("GENERIC", "Error in pre-fetching DC API options: " + err)
            credentialRequestOptions.value[profile.name] = null
            throw err
        }
    }

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
            credentials: credentials,
            profileLabel: profile.label,
            profileName: profile.name,
            presentationMechanismIdentifier: "dcql_query",
            includeWrpac: reqSelection.value.includeWrpac || false,
            includeWrprc: reqSelection.value.includeWrprc || false,
        }
        console.log('updateProfile result', reqSelection.value)

        handleRequestChanged()
    }

    async function updateSchemeType(credential, schemeType) {
        console.log('updateSchemeType', schemeType)
        credential.schemeType = schemeType
        credential.attributes = schemeType.attributes
        credential.validRepresentations = schemeType.validRepresentations
        credential.sd = schemeType.sd
        handleRequestChanged()
    }

    async function updatePresentationMechanismIdentifier(presentationMechanismIdentifier) {
        console.log('updatePresentationMechanismIdentifier', presentationMechanismIdentifier)
        reqSelection.value.presentationMechanismIdentifier = presentationMechanismIdentifier
        handleRequestChanged()
    }

    async function updateIncludeWrpac(includeWrpac) {
        console.log('updateIncludeWrpac', includeWrpac)
        reqSelection.value.includeWrpac = includeWrpac
        handleRequestChanged()
    }

    async function updateIncludeWrprc(includeWrprc) {
        console.log('updateIncludeWrprc', includeWrprc)
        reqSelection.value.includeWrprc = includeWrprc
        handleRequestChanged()
    }

    async function updateRepresentation(credential, representation) {
        console.log('updateRepresentation', representation)
        credential.representation = representation
        handleRequestChanged()
    }

    async function updateAttribute(attribute) {
        console.log('updateAttribute', attribute)
        attribute.isSelected = !attribute.isSelected
        handleRequestChanged()
    }

    async function addCredential() {
        console.log('addCredential')
        reqSelection.value.credentials.push({
            "schemeType": null,
            "representation": null,
            "sd": null,
            "attributes": []
        })
        handleRequestChanged()
    }

    async function removeCredential(credential) {
        console.log('removeCredential', credential)
        const index = reqSelection.value.credentials.indexOf(credential)
        reqSelection.value.credentials.splice(index, 1)
        handleRequestChanged()
    }

    async function updatePresentationDefinition(presentationDefinition) {
        console.log('updatePresentationDefinition', presentationDefinition)
        reqSelection.value.presentationDefinition = presentationDefinition
        compareRequestChanged()
    }

    async function updateDcqlQuery(dcqlQuery) {
        console.log('updateDcqlQuery', dcqlQuery)
        reqSelection.value.dcqlQuery = dcqlQuery
        compareRequestChanged()
    }

    async function updateDeviceRequest(deviceRequest) {
        console.log('updateDeviceRequest', deviceRequest)
        reqSelection.value.deviceRequest = deviceRequest
        compareRequestChanged()
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
            if (schemeType == null || typeof schemeType != "object" || typeof schemeType.value != "string") {
                errors.push("Credential Type not set")
            } else if (!allowedSchemeTypes.includes(schemeType.value)) {
                errors.push("Credential Type invalid")
            }

            // check representation type
            const representation = credential.representation
            if (representation == null || typeof representation != "object" || typeof representation.value != "string") {
                errors.push("Presentation Type not set")
            } else if (!allowedRepresentations.includes(representation.value)) {
                errors.push("Presentation Type invalid")
            }
        }

        return errors
    }

    function createRequestJSON() {
        var presentationDefinition = reqSelection.value.presentationDefinition === "" ? null : reqSelection.value.presentationDefinition
        var dcqlQuery = reqSelection.value.dcqlQuery === "" ? null : reqSelection.value.dcqlQuery
        var deviceRequest = reqSelection.value.deviceRequest === "" ? null : reqSelection.value.deviceRequest

        if(presentationDefinition != null) {
            presentationDefinition = JSON.parse(presentationDefinition)
        }
        if(dcqlQuery != null) {
            dcqlQuery = JSON.parse(dcqlQuery)
        }
        const request = {
            presentationMechanismIdentifier: reqSelection.value.presentationMechanismIdentifier,
            presentationDefinition: presentationDefinition,
            dcqlQuery: dcqlQuery,
            deviceRequest: deviceRequest,
            includeWrpac: reqSelection.value.includeWrpac,
            includeWrprc: reqSelection.value.includeWrprc,
        }

        return JSON.stringify(request)
    }

    async function invokeDCAPI(url, selection) {
        try {
            clearError()

            const validationErrors = validateDcApiSelection(selection);
            if (validationErrors.length > 0) {
                setError("GENERIC", validationErrors.join(" "));
                return;
            }

            if (reqResult.value == null || reqResult.value.profiles == null)
                return;

            const requestUrl = new URL(url)
            const segments = requestUrl.pathname.split('/').filter(Boolean);
            const id = segments.pop();

            const profile = reqResult.value.profiles.find(p => p.url === url || p.dcApiUrl === url);
            if (!profile) {
                throw new Error("Profile not found for url: " + url);
            }

            const credentialRequestOptionsToUse = credentialRequestOptions.value[profile.name];

            if (!credentialRequestOptionsToUse) {
                setError("GENERIC", "Credential request options not ready. They might still be loading. Please try again shortly.");
                console.error("Credential request options not found for profile:", profile.name);
                return;
            }

            console.log(`Credential request options: `, credentialRequestOptionsToUse)

            const walletResponse = await navigator.credentials.get(credentialRequestOptionsToUse);

            let backendRequest = {};
            if (walletResponse.constructor.name === 'DigitalCredential') {
                const data = walletResponse.data
                const protocol = walletResponse.protocol
                console.log("Response Data: " + data + " Protocol: " + protocol)
                backendRequest = {protocol: protocol, data: data, origin: location.origin}
            } else {
                throw new Error("Digital Credentials response not understood")
            }

            const serverResponse = await fetch(URLs.postUrl + id, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(backendRequest)
            });

            let serverResponseJson = await serverResponse.json();
            console.log(`Server Response: `, serverResponseJson)

            if (!serverResponse.ok) {
                throw new Error('Network response was not ok: ' + serverResponseJson.error);
            }

            clearError()
        } catch (err) {
            console.log('error: ', err)
            setError("GENERIC", "Error in DC API: " + err)
        }
    }

    async function generateQrCode() {
        console.log('generateQrCode', reqSelection.value)

        try {
            const validationErrors = validate()
            if (validationErrors.length > 0) {
                reqResult.value = null
                setError("GENERIC", "Validation Error: " + validationErrors.join(", "))
                return
            }

            await handleRequestChanged()

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
                console.log('generateQrCode: got ', data)
                reqResult.value = {
                    profiles: data.profiles
                }

                const newSelections = {}
                const newCredentialRequestOptions = {}
                for (const profile of data.profiles) {
                    newSelections[profile.name] = {
                        signedOid4vp: true, // Default to signed for initial pre-fetch
                    }
                    newCredentialRequestOptions[profile.name] = null
                }
                selections.value = newSelections
                credentialRequestOptions.value = newCredentialRequestOptions

                clearError()
                resetRequestChanged()
            } else {
                const data = (await response.text())
                console.log(`generateQrCode: error ${data}`)
                throw data
            }
        } catch (err) {
            console.log(`error: ${err}`)
            reqResult.value = null
            setError("GENERIC", "Error generating request: " + err)
            throw err
        }
    }

    async function loadResult() {
        try {
            if (reqResult.value == null || reqResult.value.profiles == null)
                return;
            for (const item of reqResult.value.profiles) {
                let response = await fetch(URLs.resultUrl + item.id);
                if (response.ok) {
                    try {
                        const data = await response.json();
                        if (data != null) {
                            console.log('loadResult got: ', data);
                            // navigate to the success page
                            window.location.href = URLs.successPageUrl + item.id
                        }
                    } catch (error) {
                        // do nothing
                    }
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

    function compareRequestedCredentialsChanged() {
        try {
            const requestBuilderJSON = createRequestBuilderJSON()
            const changed = requestBuilderJSON != requestedCredentialsChanged.value.oldRequestedCredentialsJSON
            requestedCredentialsChanged.value.changed = changed
        } catch (error) {
            requestedCredentialsChanged.value.changed = true
        }
    }

    function compareRequestChanged() {
        compareRequestedCredentialsChanged()
        if(requestedCredentialsChanged.value.changed) {
            reqChanged.value.changed = true
        } else {
            try {
                const requestJSON = createRequestJSON()
                const changed = requestJSON != reqChanged.value.oldRequestJSON
                reqChanged.value.changed = changed
            } catch (error) {
                reqChanged.value.changed = true
            }
        }
    }

    async function handleRequestChanged() {
        compareRequestChanged()
        if(requestedCredentialsChanged.value.changed == false) {
            return
        }

        if (requestQueriesPromise != null) {
            await requestQueriesPromise
            return
        }

        requestQueriesPromise = (async () => {
            // refresh credential queries
            const requestBuilderJson = createRequestBuilderJSON()
            const newQueries = await buildCredentialQueries(requestBuilderJson)
            requestedCredentialsChanged.value.oldRequestedCredentialsJSON = requestBuilderJson
            requestedCredentialsChanged.value.changed = false

            reqSelection.value.presentationDefinition = newQueries["presentationDefinition"]
            reqSelection.value.presentationDefinitionError = newQueries["presentationDefinitionError"]
            reqSelection.value.dcqlQuery = newQueries["dcqlQuery"]
            reqSelection.value.dcqlQueryError = newQueries["dcqlQueryError"]
            reqSelection.value.deviceRequest = newQueries["deviceRequest"]
            reqSelection.value.deviceRequestError = newQueries["deviceRequestError"]
        })()

        try {
            await requestQueriesPromise
        } finally {
            requestQueriesPromise = null
        }
    }

    watch([selections, activeProfile], async () => {
        const profile = activeProfile.value
        if (!profile) return
        if (!selections.value[profile.name]) return
        if (!(profile.supportedOptions?.includes('OID4VP_DC_API') || profile.supportedOptions?.includes('ISO_MDOC_DC_API'))) {
            return
        }
        await prefetchCredentialRequestOptions(profile, selections.value[profile.name])
    }, {deep: true})

    watch(activeProfile, (profile) => {
        clearError()
        if (!(profile?.supportedOptions?.includes('OID4VP_DC_API') || profile?.supportedOptions?.includes('ISO_MDOC_DC_API'))) {
            return
        }
        const currentSelection = selections.value[profile.name] || {}
        if (typeof currentSelection.signedOid4vp !== 'boolean') {
            selections.value[profile.name] = {
                ...currentSelection,
                signedOid4vp: true,
            }
        }
    })

    watch(() => error.value.type, async (type) => {
        if (!type) return
        await nextTick()
        document.getElementById('error-alert')?.scrollIntoView({behavior: 'smooth', block: 'start'})
    })

    watch(registrationEndpointBaseUrl, (value) => {
        if (typeof window === "undefined") return
        try {
            window.localStorage?.setItem("registrationEndpointBaseUrl", value || "")
        } catch (_ignored) {
            // ignore persistence errors in restricted browser modes
        }
    })

    updateProfile(config.profiles[0])
    loadRegistrationState()
    loadCertificateOptions()
    loadCertificatePreviews()

    async function loadCertificateOptions() {
        try {
            const response = await fetch("api/wrp/cert-options")
            if (response.ok) {
                certOptions.value = await response.json()
                syncCertificateSelection()
            }
        } catch (err) {
            console.log('loadCertificateOptions error: ', err)
        }
    }

    async function loadCertificatePreviews() {
        try {
            const response = await fetch("api/wrp/certs")
            if (response.ok) {
                certPreviews.value = await response.json()
                syncCertificateSelection()
            }
        } catch (err) {
            console.log('loadCertificatePreviews error: ', err)
        }
    }

    function syncCertificateSelection() {
        const hasWrpac = certOptions.value.some(item => item.id === "wrpac")
        const hasWrprc = certPreviews.value.some(item => item.id === "wrprc")
        if (!hasWrpac && reqSelection.value.includeWrpac) {
            reqSelection.value.includeWrpac = false
        }
        if (!hasWrprc && reqSelection.value.includeWrprc) {
            reqSelection.value.includeWrprc = false
        }
    }

    async function loadRegistrationState() {
        try {
            const response = await fetch("api/wrp/registration")
            if (response.ok) {
                registrationState.value = await response.json()
            }
        } catch (err) {
            console.log('loadRegistrationState error: ', err)
        }
    }

    async function refreshRegistrationStateFromRegistrar(options = {}) {
        try {
            const response = await fetch("api/wrp/registration/refresh", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    endpointBaseUrl: options.endpointBaseUrl ?? registrationEndpointBaseUrl.value,
                    registrarEmail: options.registrarEmail ?? registrarEmail.value,
                    registrarPassword: options.registrarPassword ?? registrarPassword.value,
                }),
                signal: options.signal,
            })
            if (!response.ok) {
                throw new Error(await parseErrorResponse(response))
            }
            registrationState.value = await response.json()
            return true
        } catch (err) {
            if (err?.name === "AbortError") {
                return false
            }
            if (options.suppressGlobalError) {
                options.onError?.("Refresh registration failed: " + err)
            } else {
                setError("GENERIC", "Refresh registration failed: " + err)
            }
            return false
        }
    }

    async function resetRegistrationForTerminalServicePoint(options = {}) {
        clearError()
        try {
            const response = await fetch("api/wrp/registration/reset", {
                method: "POST",
            })
            if (!response.ok) {
                throw new Error(await parseErrorResponse(response))
            }
            await loadRegistrationState()
            return true
        } catch (err) {
            if (options.suppressGlobalError) {
                options.onError?.("Reset registration failed: " + err)
            } else {
                setError("GENERIC", "Reset registration failed: " + err)
            }
            return false
        }
    }

    watch(registrarEmail, (value) => {
        if (typeof window === "undefined") return
        try {
            window.localStorage?.setItem("registrarEmail", value || "")
        } catch (_ignored) {
            // ignore persistence errors in restricted browser modes
        }
    })
    watch(registrarPassword, (value) => {
        if (typeof window === "undefined") return
        try {
            window.localStorage?.setItem("registrarPassword", value || "")
        } catch (_ignored) {
            // ignore persistence errors in restricted browser modes
        }
    })

    async function registerWrpForTerminalServicePoint(wrpPayload, options = {}) {
        clearError()
        try {
            const response = await fetch("api/wrp/registration/wrp", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(wrpPayload),
                signal: options.signal,
            })
            if (!response.ok) {
                throw new Error(await parseErrorResponse(response))
            }
            await loadRegistrationState()
            return true
        } catch (err) {
            if (err?.name === "AbortError") {
                return false
            }
            if (options.suppressGlobalError) {
                options.onError?.("Register WRP failed: " + err)
            } else {
                setError("GENERIC", "Register WRP failed: " + err)
            }
            return false
        }
    }

    async function registerServiceForTerminalServicePoint(servicePayload, options = {}) {
        clearError()
        try {
            const response = await fetch("api/wrp/registration/service", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(servicePayload),
                signal: options.signal,
            })
            if (!response.ok) {
                throw new Error(await parseErrorResponse(response))
            }
            await loadRegistrationState()
            return true
        } catch (err) {
            if (err?.name === "AbortError") {
                return false
            }
            if (options.suppressGlobalError) {
                options.onError?.("Register service failed: " + err)
            } else {
                setError("GENERIC", "Register service failed: " + err)
            }
            return false
        }
    }

    async function loadWrprcFromUrl(url, options = {}) {
        clearError()
        try {
            const response = await fetch("api/wrp/registration/load-wrprc", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    url,
                    endpointBaseUrl: options.endpointBaseUrl ?? registrationEndpointBaseUrl.value,
                    registrarEmail: options.registrarEmail ?? registrarEmail.value,
                    registrarPassword: options.registrarPassword ?? registrarPassword.value,
                }),
            })
            if (!response.ok) {
                throw new Error(await parseErrorResponse(response))
            }
            await Promise.all([
                loadRegistrationState(),
                loadCertificatePreviews(),
            ])
        } catch (err) {
            if (options.suppressGlobalError) {
                options.onError?.("Fetch Existing WRPRC failed: " + err)
            } else {
                setError("GENERIC", "Fetch Existing WRPRC failed: " + err)
            }
            return false
        }
        return true
    }

    async function loadWrpacFromUrl(url, options = {}) {
        clearError()
        try {
            const response = await fetch("api/wrp/registration/load-wrpac", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    url,
                    endpointBaseUrl: options.endpointBaseUrl ?? registrationEndpointBaseUrl.value,
                    registrarEmail: options.registrarEmail ?? registrarEmail.value,
                    registrarPassword: options.registrarPassword ?? registrarPassword.value,
                }),
            })
            if (!response.ok) {
                throw new Error(await parseErrorResponse(response))
            }
            await Promise.all([
                loadRegistrationState(),
                loadCertificateOptions(),
                loadCertificatePreviews(),
            ])
            return true
        } catch (err) {
            if (options.suppressGlobalError) {
                options.onError?.("Fetch Existing WRPAC failed: " + err)
            } else {
                setError("GENERIC", "Fetch Existing WRPAC failed: " + err)
            }
            return false
        }
    }

    async function issueWrpacForTerminalServicePoint(options = {}) {
        clearError()
        try {
            const response = await fetch("api/wrp/registration/issue-wrpac", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    endpointBaseUrl: options.endpointBaseUrl ?? registrationEndpointBaseUrl.value,
                    registrarEmail: options.registrarEmail ?? registrarEmail.value,
                    registrarPassword: options.registrarPassword ?? registrarPassword.value,
                    wrpacProviderUrl: options.wrpacProviderUrl,
                    wrpacProviderUser: options.wrpacProviderUser,
                    wrpacProviderPassword: options.wrpacProviderPassword,
                }),
                signal: options.signal,
            })
            if (!response.ok) {
                throw new Error(await parseErrorResponse(response))
            }
            await Promise.all([
                loadRegistrationState(),
                loadCertificateOptions(),
                loadCertificatePreviews(),
            ])
            return true
        } catch (err) {
            if (err?.name === "AbortError") {
                return false
            }
            if (options.suppressGlobalError) {
                options.onError?.("Issue WRPAC failed: " + err)
            } else {
                setError("GENERIC", "Issue WRPAC failed: " + err)
            }
            return false
        }
    }

    async function issueWrprcForTerminalServicePoint(options = {}) {
        clearError()
        try {
            const response = await fetch("api/wrp/registration/issue-wrprc", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    endpointBaseUrl: options.endpointBaseUrl ?? registrationEndpointBaseUrl.value,
                    registrarEmail: options.registrarEmail ?? registrarEmail.value,
                    registrarPassword: options.registrarPassword ?? registrarPassword.value,
                    maxAttempts: options.maxAttempts,
                    pollIntervalMs: options.pollIntervalMs,
                }),
                signal: options.signal,
            })
            if (!response.ok) {
                throw new Error(await parseErrorResponse(response))
            }
            await Promise.all([
                loadRegistrationState(),
                loadCertificatePreviews(),
            ])
            return true
        } catch (err) {
            if (err?.name === "AbortError") {
                return false
            }
            if (options.suppressGlobalError) {
                options.onError?.("Issue WRPRC failed: " + err)
            } else {
                setError("GENERIC", "Issue WRPRC failed: " + err)
            }
            return false
        }
    }

    function buildRegistrationEndpoints() {
        const base = (registrationEndpointBaseUrl.value || "").trim().replace(/\/+$/, "")
        const state = registrationState.value || {}
        const wrpIdentifier = state.wrpIdentifier ? encodeURIComponent(state.wrpIdentifier) : null
        const serviceUri = state.serviceUri ? encodeURIComponent(state.serviceUri) : null
        return {
            base,
            registerWrpUrl: base ? `${base}/api/rp/wrps` : "",
            registerServiceUrl: base && wrpIdentifier ? `${base}/api/rp/wrps/${wrpIdentifier}/services` : "",
            wrpacUrl: base && wrpIdentifier ? `${base}/api/rp/wrps/${wrpIdentifier}/wrpac` : "",
            wrprcUrl: base && wrpIdentifier && serviceUri ? `${base}/api/rp/wrps/${wrpIdentifier}/wrprc?serviceUri=${serviceUri}` : "",
        }
    }

    async function saveWrprcForTesting(jws, options = {}) {
        clearError()
        try {
            const response = await fetch("api/wrp/wrprc", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({jws}),
            })
            if (!response.ok) {
                throw new Error(await parseErrorResponse(response))
            }
            await Promise.all([
                loadRegistrationState(),
                loadCertificatePreviews(),
            ])
            return true
        } catch (err) {
            if (options.suppressGlobalError) {
                options.onError?.("Upload WRPRC failed: " + err)
            } else {
                setError("GENERIC", "Upload WRPRC failed: " + err)
            }
            return false
        }
    }

    // --- RETURNS -------------------------------------------------

    return {
        config,
        certOptions,
        certPreviews,
        registrationState,
        registrationEndpointBaseUrl,
        registrarEmail,
        registrarPassword,
        reqSelection,
        selections,
        reqResult,
        activeProfileName,
        activeProfile,
        error,
        reqChanged,
        updateProfile,
        updateSchemeType,
        updateRepresentation,
        updatePresentationMechanismIdentifier,
        updateIncludeWrpac,
        updateIncludeWrprc,
        addCredential,
        removeCredential,
        updatePresentationDefinition,
        updateDcqlQuery,
        updateDeviceRequest,
        updateAttribute,
        generateQrCode,
        startPeriodicUpdate,
        resetRequestChanged,
        compareRequestChanged,
        invokeDCAPI,
        registerWrpForTerminalServicePoint,
        registerServiceForTerminalServicePoint,
        resetRegistrationForTerminalServicePoint,
        loadCertificateOptions,
        loadCertificatePreviews,
        loadRegistrationState,
        refreshRegistrationStateFromRegistrar,
        loadWrpacFromUrl,
        loadWrprcFromUrl,
        issueWrpacForTerminalServicePoint,
        issueWrprcForTerminalServicePoint,
        saveWrprcForTesting,
        buildRegistrationEndpoints,
    }
}

export {createBasicSetup}
