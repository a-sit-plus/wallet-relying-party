import AttributeSelectComp from './attribute-select-comp.js'

export default {
    props: {
        request: {},
        config: {},
        singleColumn: { default: false },
        qrButtonText: { default: "Refresh Request" },
        // Optional active profile or name coming from the login options tabs
        activeProfile: { default: null },
        activeProfileName: { default: null }
    },
    components: {
        'attribute-select-comp': AttributeSelectComp
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
                        {{ presentationMechanism.label }} - <span class="text-primary font-monospace">{{ presentationMechanism.value }}</span>
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
                        {{ item.label }} - <span class="text-primary font-monospace">{{ item.value }}</span>
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
                        {{ item.label }} - <span class="text-primary font-monospace">{{ item.value }}</span>
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
                <attribute-select-comp :credential="credential"
                                       @update-attribute="$emit('updateAttribute', $event)">
                </attribute-select-comp>
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
