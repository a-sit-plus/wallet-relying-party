export default {
    props: {
        request: {},
        config: {},
        registrationState: { default: null },
        certOptions: { default: null },
        certPreviews: { default: null },
        showCertificates: { default: false },
        singleColumn: { default: false },
        qrButtonText: { default: "Refresh Request" },
        // Optional active profile or name coming from the login options tabs
        activeProfile: { default: null },
        activeProfileName: { default: null }
    },
    emits: [
        'updateSchemeType',
        'updateRepresentation',
        'updatePresentationMechanismIdentifier',
        'updateIncludeWrpac',
        'updateIncludeWrprc',
        'updateAttribute',
        'addCredential',
        'removeCredential',
        'generateQrCode'
    ],
    template: `


<div class="row pt-2" v-if="showCertificates">
    <legend class="">
        Verifier Certificates
    </legend>
    <div class="d-flex flex-wrap gap-2 mb-2">
        <a class="btn btn-outline-primary btn-sm" href="/registration.html">Open Registration Menu</a>
        <a class="btn btn-outline-secondary btn-sm" href="/certificates.html">Open Certificates</a>
    </div>
    <div :class="singleColumn ? '' : 'col-lg-7 overflow-hidden'">
        <fieldset class="row mb-3">
            <legend class="col-form-label col-sm-4 pt-0 fw-bold">Add Certificates to Request</legend>
            <div class="col-sm-8">
                <div class="form-check">
                    <input class="form-check-input"
                           type="checkbox"
                           name="includeWrpac"
                           :disabled="!((certPreviews && certPreviews.some(item => item.id === 'wrpac')) || (certOptions && certOptions.some(item => item.id === 'wrpac')) || (registrationState && registrationState.hasWrpac))"
                           :checked="request.includeWrpac"
                           @click="$emit('updateIncludeWrpac', !request.includeWrpac)">
                    <label class="form-check-label">
                        WRP Access Certificate (WRPAC) - <span class="text-primary">x509_hash</span>
                        <span v-if="!((certPreviews && certPreviews.some(item => item.id === 'wrpac')) || (certOptions && certOptions.some(item => item.id === 'wrpac')) || (registrationState && registrationState.hasWrpac))" class="text-muted">(not available)</span>
                    </label>
                </div>
                <div class="form-check">
                    <input class="form-check-input"
                           type="checkbox"
                           name="includeWrprc"
                           :disabled="!((certPreviews && certPreviews.some(item => item.id === 'wrprc')) || (certOptions && certOptions.some(item => item.id === 'wrprc')) || (registrationState && registrationState.hasWrprc))"
                           :checked="request.includeWrprc"
                           @click="$emit('updateIncludeWrprc', !request.includeWrprc)">
                    <label class="form-check-label">
                        WRP Registration Certificate (WRPRC) - <span class="text-primary">wrprc+jws</span>
                        <span v-if="!((certPreviews && certPreviews.some(item => item.id === 'wrprc')) || (certOptions && certOptions.some(item => item.id === 'wrprc')) || (registrationState && registrationState.hasWrprc))" class="text-muted">(not available)</span>
                    </label>
                </div>
            </div>
        </fieldset>
    </div>
</div>
<div class="row border-top pt-2" v-if="showCertificates && certPreviews && certPreviews.length">
    <legend class="">
        Certificate Preview
    </legend>
    <div :class="singleColumn ? '' : 'col-lg-7 overflow-hidden'">
        <div v-for="item in certPreviews"
             v-if="(item.id === 'wrpac' && request.includeWrpac) || (item.id === 'wrprc' && request.includeWrprc)"
             :key="item.id"
             class="mb-3">
            <div class="fw-bold mb-1">{{ item.label }} <span class="text-muted">({{ item.type }})</span></div>
            <textarea class="form-control" rows="5" readonly>{{ item.content }}</textarea>
        </div>
    </div>
</div>

<div class="row border-top pt-2">
    <legend class="">
        Request Details
    </legend>
    <div :class="singleColumn ? '' : 'col-lg-7 overflow-hidden'">
        <fieldset class="row mb-3">
            <legend class="col-form-label col-sm-4 pt-0 fw-bold">Presentation Mechanism (Unsupported for DC API)</legend>
            <div class="col-sm-8">
                <div v-for="presentationMechanism in config.presentationMechanisms"
                     :key="presentationMechanism.value"
                     class="form-check">
                    <input class="form-check-input" type="radio"
                           :name="presentationMechanism.label"
                           :value="presentationMechanism.value"
                           :checked="request.presentationMechanismIdentifier && request.presentationMechanismIdentifier == presentationMechanism.value"
                           @click="$emit('updatePresentationMechanismIdentifier', presentationMechanism.value)">
                    <label class="form-check-label">
                        {{ presentationMechanism.label }} - <span class="text-primary">{{ presentationMechanism.value }}</span>
                    </label>
                </div>
            </div>
        </fieldset>
    </div>
</div>
<div class="row border-top pt-2"
     v-for="credential in request.credentials"
     :key="credential.schemeType">
    <legend class="">
        <span v-if="credential.schemeType && credential.schemeType.label">
            Credential: {{ credential.schemeType.label }}
        </span>
        <span v-else>
            Credential: Unknown
        </span>
        <button type="button" class="btn btn-light" @click="$emit('removeCredential', credential)">
            <i class="bi-trash3"></i>
        </button>
    </legend>
    <div :class="singleColumn ? '' : 'col-lg-7 overflow-hidden'">
        <fieldset class="row mb-3">
            <legend class="col-form-label col-sm-4 pt-0 fw-bold">Credential Type</legend>
            <div class="col-sm-8">
                <div v-for="item in config.schemeTypes"
                     :key="credential.schemeType + '-' + item.value"
                     class="form-check">
                    <input class="form-check-input" type="radio"
                           :name="(credential.schemeType ? credential.schemeType.label : 'unknown') + '-credentialScheme'"
                           :value="item.value"
                           :checked="credential.schemeType && credential.schemeType.value == item.value"
                           @click="$emit('updateSchemeType', credential, item)">
                    <label class="form-check-label">
                        {{ item.label }} - <span class="text-primary">{{ item.value }}</span>
                    </label>
                </div>
            </div>
        </fieldset>

        <fieldset class="row mb-3">
            <legend class="col-form-label col-sm-4 pt-0 fw-bold">Presentation Type</legend>
            <div class="col-sm-8">
                <div v-for="item in config.representation"
                     :key="credential.schemeType + '-' + item.value"
                     class="form-check">
                    <input class="form-check-input" type="radio"
                           :name="(credential.schemeType ? credential.schemeType.label : 'unknown') + '-representation'"
                           :value="item.value"
                           :disabled="credential.validRepresentations && !credential.validRepresentations.includes(item.value)"
                           :checked="credential.representation && credential.representation.value == item.value"
                           @click="$emit('updateRepresentation', credential, item)">
                    <label class="form-check-label">
                        {{ item.label }} - <span class="text-primary">{{ item.value }}</span>
                        <span v-if="credential.validRepresentations && credential.validRepresentations.includes(item.value) == false"> (Not supported)</span>
                    </label>
                </div>
            </div>
        </fieldset>
    </div>

    <div :class="singleColumn ? '' : 'col-lg-5'">
        <fieldset class="row mb-3">
            <legend class="col-form-label col-sm-4 pt-0 fw-bold">Attributes</legend>
            <div class="col-sm-8">
                <p v-if="credential.attributes.length == 0">Please select a Credential Type.</p>
                <div v-if="credential.sd == true || (credential.representation && credential.representation.value != 'SD_JWT')"
                     v-for="item in credential.attributes"
                     :key="credential.schemeType + '-' + item.value"
                     class="form-check">
                    <input class="attributes form-check-input"
                           type="checkbox"
                           :name="(credential.schemeType ? credential.schemeType.label : 'unknown') + '-attributes'"
                           :value="item.value"
                           :checked="item.isSelected"
                           @click="$emit('updateAttribute', item)">
                    <label class="form-check-label" for="attributes1">
                        {{ item.label }}
                    </label>
                </div>
                <div v-if="credential.sd == false && (credential.representation && credential.representation.value == 'SD_JWT')" >
                    <p>For this credential, attributes can not be selectively disclosed.</p>
                    <ul class="list-group list-group-flush">
                        <li class="list-group-item" v-for="item in credential.attributes">{{ item.label }}</li>
                    </ul>
                </div>
            </div>
        </fieldset>
    </div>
</div>

<div class="row border-top pt-2">
    <p>
        <button type="button" class="btn btn-light" @click="$emit('addCredential')"><i class="bi-plus"></i>Add
            Requested Credential
        </button>
    </p>
</div>

<div class="row mb-3">
    <div class="d-flex justify-content-center">
        <button @click="$emit('generateQrCode')" class="btn btn-primary">{{ qrButtonText }}</button>
    </div>
</div>
`
}
