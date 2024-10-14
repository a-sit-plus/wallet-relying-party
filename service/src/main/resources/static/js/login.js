import {createApp, ref} from 'vue'

// --- STATIC --------------------------------------------------------

const URLs = {
    loginConfigUrl: 'js/login-config.json',
    transactionUrl: 'transaction/create',
    resultUrl: 'api/single/',
}

const loadConfig = async function() {
    // load from json
    const response = await fetch(URLs.loginConfigUrl);
    const config = await response.json();
    //console.log(config);

    return config;
}

const createBasicSetup = function(config) {

    // --- STATE ---------------------------------------------------

    const reqSelection = ref({urlprefix: null, credentials: []})
    const error = ref({message: null})
    const reqResult = ref({
        id: null,
        qrCodeSrc: null,
        linkSrc: null,
    })
    const transactionResult = ref({item: null})
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
                const selected = credential.attributes.reduce(function (acc, attr) {
                    if (attr.isSelected) acc.push(attr.value)
                    return acc
                }, [])

                // take all attribtues from config for this credential type, and mark some as isSelected
                attrs = schemeType.attributes.map(function (attr) {
                    attr.isSelected = selected.includes(attr.value)
                    return attr
                })
            }

            credentials.push({
                schemeType: schemeType,
                representation: representation,
                attributes: attrs
            })
        }

        reqSelection.value = {
            urlprefix: urlprefix,
            credentials: credentials
        }

        console.log('updateProfile result', reqSelection.value)
    }

    async function updateSchemeType(credential, schemeType) {
        console.log('updateSchemeType', schemeType)
        credential.schemeType = schemeType
        credential.attributes = schemeType.attributes
    }

    async function updateRepresentation(credential, representation) {
        console.log('updateRepresentation', representation)
        credential.representation = representation
    }

    async function updateUrlprefix(urlprefix) {
        console.log('updateUrlprefix', urlprefix)
        reqSelection.value.urlprefix = urlprefix
    }

    async function updateAttribute(attribute) {
        console.log('updateAttribute', attribute)
        attribute.isSelected = !attribute.isSelected;
    }

    async function addCredential() {
        console.log('addCredential')
        reqSelection.value.credentials.push({
            "schemeType": null,
            "representation": null,
            "attributes": []
        })
    }

    async function removeCredential(credential) {
        console.log('removeCredential', credential)
        const index = reqSelection.value.credentials.indexOf(credential)
        reqSelection.value.credentials.splice(index, 1)
    }

    function validate () {
        const errors = [];

        // get allowed values
        const allowedPrefixes = config.urlprefix.map(function (x) {
            return x.value
        })
        const allowedSchemeTypes = config.schemeTypes.map(function (x) {
            return x.value
        })
        const allowedRepresentations = config.representation.map(function (x) {
            return x.value
        })

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

        return errors;
    }

    async function generateQrCode() {
        console.log('generateQrCode', reqSelection.value)

        try {
            const validationErrors = validate();
            if (validationErrors.length > 0) {
                reqResult.value = null
                error.value.message = "Validation Error: " + validationErrors.join(", ")
                return;
            }

            // build request payload
            // from form inputs, take values and only selected attributes
            const credentials = reqSelection.value.credentials.map(function (credential) {
                return {
                    credentialType: credential.schemeType.value,
                    representation: credential.representation.value,
                    attributes: credential.attributes.filter(function (attr) {
                        return attr.isSelected
                    }).map(function (attr) {
                        return attr.value
                    }),
                }
            })
            const request = {
                urlprefix: reqSelection.value.urlprefix.value,
                credentials: credentials,
            }
            console.log(`generateQrCode: fetching ${JSON.stringify(request)}`)

            // request QR code, link url and id
            const response = await fetch(URLs.transactionUrl, {
                method: 'POST',
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(request),
            })
            if (response.ok) {
                const data = await response.json();
                console.log(`generateQrCode: got ${data}`);
                reqResult.value = {
                    qrCodeSrc: "data:image/png;base64," + data.qrCodePng,
                    linkSrc: data.qrCodeUrl,
                    id: data.id,
                }
                startPeriodicUpdate();
                error.value.message = null
            } else {
                const data = (await response.text())
                console.log(`generateQrCode: error ${data}`)
                throw data
            }
        } catch (err) {
            console.log(`error: ${err}`)
            reqResult.value = null
            error.value.message = "Error: " + err
        }
    }

    async function loadResult() {
        try {
            if (reqResult.value.id == null)
                return;
            console.log('loadResult for ' + reqResult.value.id);
            let response = await fetch(URLs.resultUrl + reqResult.value.id);
            const data = await response.json();
            console.log('loadResult got: ', data);
            if (response.ok) {
                transactionResult.value.item = data;
                stopPeriodicUpdate()
            }
        } catch (error) {
            console.log('error: ', error);
        }
    }

    async function startPeriodicUpdate() {
        if (resultInterval == null) {
            loadResult() // initially
            resultInterval = setInterval(loadResult, 2000) // periodically
        }
    }

    async function stopPeriodicUpdate() {
        if (resultInterval != null) {
            clearInterval(resultInterval)
            resultInterval = null
        }
    }

    updateProfile(config.profiles[0]);

    // --- FORMATTERS ----------------------------------------------------

    function toggleDetails(item) {
        // show details of item
        item.showDetails = !(item.showDetails == true)
    }

    function filterClaims(item) {
        const deepCopy = JSON.parse(JSON.stringify(item));
        delete deepCopy.timestamp;
        delete deepCopy.credentials;
        delete deepCopy.expiredTime;
        delete deepCopy.imageDataBase64;
        delete deepCopy.showDetails;
        return deepCopy;
    }

    // --- CHECKER -------------------------------------------------------

    function isSet(value) {
        return value && value != "N/A" && value != "data:image;base64,null" ;
    }

    // --- RETURNS -------------------------------------------------

    return {
        config,
        reqSelection,
        reqResult,
        error,
        transactionResult,
        updateProfile,
        updateSchemeType,
        updateRepresentation,
        updateUrlprefix,
        addCredential,
        removeCredential,
        updateAttribute,
        generateQrCode,
        toggleDetails,
        filterClaims,
        isSet
    }
}

export { loadConfig, createBasicSetup }
