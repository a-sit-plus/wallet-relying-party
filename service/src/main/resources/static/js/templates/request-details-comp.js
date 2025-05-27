export default {
    props: {
        request: {},
        config: {},
        singleColumn: { default: false },
        qrButtonText: { default: "Refresh Request" }
    },
    emits: [
        'updateSchemeType',
        'updateRepresentation',
        'updatePresentationMechanismIdentifier',
        'updateAttribute',
        'addCredential',
        'removeCredential',
        'generateQrCode'
    ],
    template: `


<div class="row border-top pt-2">
    <legend class="">
        Request Details
    </legend>
    <div :class="singleColumn ? '' : 'col-lg-7 overflow-hidden'">
        <fieldset class="row mb-3">
            <legend class="col-form-label col-sm-4 pt-0 fw-bold">Presentation Mechanism</legend>
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
                           :checked="credential.representation && credential.representation.value == item.value"
                           @click="$emit('updateRepresentation', credential, item)">
                    <label class="form-check-label">
                        {{ item.label }} - <span class="text-primary">{{ item.value }}</span>
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
