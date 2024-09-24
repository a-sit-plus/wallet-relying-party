import {createApp, ref} from 'vue'

// --- STATIC --------------------------------------------------------

const URLs = {
    loginConfigUrl: 'js/login-config.json',
    qrCodeUrl: 'siopv2/generateQrCode',
    qrCodeUrlUrl: 'siopv2/generateQrCodeUrl'
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

    const reqSelection = ref({})
    const error = ref({message: null})
    const qrCode = ref({src: null})
    const qrCodeUrl = ref({message: null})

    // --- FUNCTIONS -----------------------------------------------

    async function updateProfile(profile) {
        console.log('updateProfile', profile)
        reqSelection.value = {...profile}

        const schemeType = config.schemeTypes.find((type) => type.value == profile.schemeType)
        const representation = config.representation.find((type) => type.value == profile.representation)

        if (schemeType) {
            const selected = profile.attributes.reduce(function (acc, attr) {
                if (attr.isSelected) acc.push(attr.value)
                return acc
            }, [])

            var attrs = schemeType.attributes.map(function (attr) {
                attr.isSelected = selected.includes(attr.value)
                return attr
            })

            reqSelection.value.attributes = attrs
        }
    }

    async function updateSchemeType(schemeType) {
        console.log('updateSchemeType', schemeType)
        reqSelection.value.schemeType = schemeType.value
        reqSelection.value.attributes = schemeType.attributes
    }

    async function updateRepresentation(representation) {
        console.log('updateRepresentation', representation)
        reqSelection.value.representation = representation.value
    }

    async function updateUrlprefix(urlprefix) {
        console.log('updateUrlprefix', urlprefix)
        reqSelection.value.urlprefix = urlprefix.value
    }

    async function updateAttribute(attribute) {
        console.log('updateAttribute', attribute)
        attribute.isSelected = !attribute.isSelected;
    }

    async function generateQrCode() {
        console.log('generateQrCode', reqSelection.value)

        try {
            const attrs = reqSelection.value.attributes.filter(function (attr) {
                return attr.isSelected
            }).map(function (attr) {
                return attr.value
            })

            const request = {
                credentialType: reqSelection.value.schemeType,
                representation: reqSelection.value.representation,
                urlprefix: reqSelection.value.urlprefix,
                attributes: attrs,
            }
            console.log(`generateQrCode: fetching ${JSON.stringify(request)}`)

            const response = await fetch(URLs.qrCodeUrl, {
                method: 'POST',
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(request),
            })
            if (response.ok) {
                const data = (await response.blob());
                console.log(`generateQrCode: got ${data.size} bytes`)
                qrCode.value.src = URL.createObjectURL(data)
                error.value.message = null
            } else {
                const data = (await response.text())
                console.log(`generateQrCode: error ${data}`)
                throw data
            }

            const urlResponse = await fetch(URLs.qrCodeUrlUrl, {
                method: 'POST',
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(request),
            })
            if (urlResponse.ok) {
                qrCodeUrl.value.message = await urlResponse.text()
            } else {
                qrCodeUrl.value.message = null
            }
        } catch (err) {
            console.log(`error: ${err}`)
            qrCode.value.src = null
            error.value.message = "Error: " + err
        }
    }

    updateProfile(config.profiles[0]);

    // --- RETURNS -------------------------------------------------

    return {
        config,
        reqSelection,
        error,
        qrCode,
        qrCodeUrl,
        updateProfile,
        updateSchemeType,
        updateRepresentation,
        updateUrlprefix,
        updateAttribute,
        generateQrCode,
    }
}

export { loadConfig, createBasicSetup }
