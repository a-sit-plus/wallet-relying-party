import {
    effectiveForgedSignerIds,
    effectiveSignerIds,
    mismatchedVerifierInfoSignerIds,
    selectAllowMismatchedVerifierInfo as selectionForAllowMismatchedVerifierInfo,
    selectForgedSigner as selectionForForgedSigner,
    selectSignerVerifierInfo as selectionForSignerVerifierInfo,
    signerWrprcId as resolveSignerWrprcId,
    selectOid4vpMode as selectionForOid4vpMode,
    selectSigner as selectionForSigner,
    validateDcApiSelection,
} from '../dc-api-selection.mjs'

export default {
    props: [
        'result',
        'changed',
        'error',
        'dcapiSelection',
        'isoMdocRequest',
        'profileNames'
    ],
    data() {
        return {
            activeProfileName: null,
            // Digital Credentials API availability in this browser (the DigitalCredential response interface).
            dcApiSupported: typeof window.DigitalCredential !== 'undefined'
        }
    },
    computed: {
        regularProfiles() {
            return this.displayProfiles.filter(profile => !this.isDcApiProfile(profile))
        },
        dcApiProfiles() {
            return this.displayProfiles.filter(profile => this.isDcApiProfile(profile))
        },
        displayProfiles() {
            const profiles = this.result?.profiles || []
            return this.profileNames ? profiles.filter(profile => this.profileNames.includes(profile.name)) : profiles
        }
    },
    emits: [
        'generateQrCode',
        'invokeDCAPI',
        'profileTabChanged',
        'update:dcapiSelection'
    ],
    methods: {
        effectiveSignerIds() {
            return effectiveSignerIds(this.dcapiSelection)
        },
        selectOid4vpMode(mode, profile) {
            this.$emit(
                'update:dcapiSelection',
                selectionForOid4vpMode(profile, this.dcapiSelection, mode),
            )
        },
        selectSigner(signerId, checked) {
            this.$emit('update:dcapiSelection', selectionForSigner(this.dcapiSelection, signerId, checked))
        },
        isForged(signerId) {
            return effectiveForgedSignerIds(this.dcapiSelection).includes(signerId)
        },
        selectForgedSigner(signerId, forged) {
            this.$emit('update:dcapiSelection', selectionForForgedSigner(this.dcapiSelection, signerId, forged))
        },
        signerWrprcId(profile, signer) {
            return resolveSignerWrprcId(this.dcapiSelection, signer, profile.dcApiRegistrationCertificates) ?? ''
        },
        selectSignerWrprc(signerId, value) {
            this.$emit(
                'update:dcapiSelection',
                selectionForSignerVerifierInfo(this.dcapiSelection, signerId, value === '' ? null : Number(value)),
            )
        },
        isMismatchedWrprc(profile, signerId) {
            return mismatchedVerifierInfoSignerIds(this.dcapiSelection, profile.dcApiSigners).includes(signerId)
        },
        selectAllowMismatchedVerifierInfo(allowed) {
            this.$emit(
                'update:dcapiSelection',
                selectionForAllowMismatchedVerifierInfo(this.dcapiSelection, allowed),
            )
        },
        dcApiSelectionErrors(profile) {
            return validateDcApiSelection(this.dcapiSelection, this.isoMdocRequest, profile.dcApiSigners)
        },
        canStartDcApi(profile) {
            return this.dcApiSelectionErrors(profile).length === 0
        },
        activateProfile(profileName) {
            this.activeProfileName = profileName
            this.$emit('profileTabChanged', profileName)
        },
        ensureActiveProfile() {
            const profiles = this.displayProfiles
            if (profiles.length === 0) {
                this.activeProfileName = null
                return
            }

            if (!profiles.some(profile => profile.name === this.activeProfileName)) {
                this.activateProfile(profiles[0].name)
            }
        }
    },
    watch: {
        result: {
            handler() {
                this.ensureActiveProfile()
            },
            deep: true,
            immediate: true
        }
    },
    mounted() {
        this.ensureActiveProfile()
    },
    template: `
<div v-if="result != null && result.profiles != null && changed.changed" class="z-3 position-absolute rounded w-100">
    <div class="card w-50 mx-auto mt-5 text-center">
        <div class="card-body">
            <h5 class="card-title">Request Data Changed</h5>
            <p class="card-text">You have changed the request data. Please refresh the request by clicking below.</p>
            <button @click="$emit('generateQrCode')" class="btn btn-primary">Refresh Request</button>
        </div>
    </div>
</div>

<div v-if="result != null && result.profiles != null && error.type === 'GENERIC' && !changed.changed" class="z-3 position-absolute rounded w-100">
    <div class="card w-50 mx-auto mt-5 text-center">
        <div class="card-body">
            <h5 class="card-title">Request redeemed</h5>
            <p class="card-text">An error has occurred, or the request has been cancelled. To retry, please refresh the request by clicking below.</p>
            <button @click="$emit('generateQrCode')" class="btn btn-primary">Refresh Request</button>
        </div>
    </div>
</div>

<div v-if="result != null && result.profiles != null"
     class="card mb-3"
     :class="{ 'blur' : changed.changed || error.type === 'GENERIC'}">

    <div class="card-header">
        <ul class="nav nav-tabs card-header-tabs" role="tablist">
            <li class="nav-item" v-for="profile in result.profiles">
                <button class="nav-link" data-bs-toggle="tab"
                        :data-bs-target="'#tab-' + profile.name"
                        :class="{ 'active' : activeProfileName === profile.name}"
                        @click="activateProfile(profile.name)"
                        type="button">
                    {{ profile.label }}
                </button>
            </li>
        </ul>
    </div>
    <div class="card-body container position-relative">
        <div v-if="error.type === 'INCOMPATIBLE_PRESENTATION_TYPE' && !changed.changed" class="z-3 position-absolute rounded w-100">
            <div class="card w-50 mx-auto mt-3 text-center">
                <div class="card-body">
                    <h5 class="card-title">Incompatible Presentation Type</h5>
                    <p class="card-text">The current representation is not possible together with ISO 18013-7 Annex C. Please set the presentation type to ISO mDoc and refresh the request.</p>
                </div>
            </div>
        </div>
        <div class="tab-content"
             :class="{ 'blur' : error.type === 'INCOMPATIBLE_PRESENTATION_TYPE' && !changed.changed }">
            <div v-for="profile in displayProfiles"
                 class="tab-pane"
                 role="tabpanel"
                 :id="'tab-' + profile.name"
                 :class="{ 'active' : activeProfileName === profile.name}">

                 <p>Details: {{profile.description}}</p>
                <div class="options-grid">
                <div v-if="profile.supportedOptions.includes('CROSS_DEVICE')" class="option-card border rounded p-2 bg-white">
                    <h2>Option A: Cross device</h2>
                    <p>Scan the QR code with your Wallet App:</p>
                    <div class="text-left">
                        <a target="_blank" :href="profile.url">
                            <img width="300px" :src="profile.png"/>
                        </a>
                    </div>
                </div>
                <div v-if="profile.supportedOptions.includes('SAME_DEVICE')" class="option-card border rounded p-2 bg-white">
                    <h2>Option B: Same device</h2>
                    <p>Click the following button to open the Wallet App on this device:</p>
                    <div class="text-center">
                        <a target="_blank" :href="profile.url" class="btn btn-primary m-3">Open App Wallet</a>
                    </div>
                    <p>The whole link is: <a target="_blank" :href="profile.url">{{ profile.url }}</a></p>
                </div>
                
                <div v-if="profile.supportedOptions.includes('DC_API')" class="option-card border rounded p-2 bg-white">
                    <h2>Option C: Digital Credentials API</h2>
                    <div v-if="!dcApiSupported" class="alert alert-info mb-0" role="alert">
                        This browser does not support the Digital Credentials API. Use Option A or B, or try a browser that supports <code>navigator.credentials.get({ digital })</code>.
                    </div>
                    <template v-else>
                    <p>Select the request types to offer in a single browser call:</p>
                    <fieldset class="text-start mb-2">
                        <legend class="fs-6 fw-bold">OpenID4VP</legend>
                        <div class="form-check">
                            <input class="form-check-input" type="radio" :id="'dcapi-oid4vp-none-' + profile.name"
                                   :name="'dcapi-oid4vp-' + profile.name"
                                   :checked="dcapiSelection.oid4vpMode === 'NONE'"
                                   @change="selectOid4vpMode('NONE', profile)">
                            <label class="form-check-label" :for="'dcapi-oid4vp-none-' + profile.name">None</label>
                        </div>
                        <div class="form-check">
                            <input class="form-check-input" type="radio" :id="'dcapi-oid4vp-signed-' + profile.name"
                                   :name="'dcapi-oid4vp-' + profile.name"
                                   :checked="dcapiSelection.oid4vpMode === 'SIGNED'"
                                   @change="selectOid4vpMode('SIGNED', profile)">
                            <label class="form-check-label" :for="'dcapi-oid4vp-signed-' + profile.name">Signed OpenID4VP</label>
                        </div>
                        <div class="form-check">
                            <input class="form-check-input" type="radio" :id="'dcapi-oid4vp-multisigned-' + profile.name"
                                   :name="'dcapi-oid4vp-' + profile.name"
                                   :checked="dcapiSelection.oid4vpMode === 'MULTISIGNED'"
                                   @change="selectOid4vpMode('MULTISIGNED', profile)">
                            <label class="form-check-label" :for="'dcapi-oid4vp-multisigned-' + profile.name">Multisigned OpenID4VP</label>
                        </div>
                        <div class="form-check">
                            <input class="form-check-input" type="radio" :id="'dcapi-oid4vp-unsigned-' + profile.name"
                                   :name="'dcapi-oid4vp-' + profile.name"
                                   :checked="dcapiSelection.oid4vpMode === 'UNSIGNED'"
                                   @change="selectOid4vpMode('UNSIGNED', profile)">
                            <label class="form-check-label" :for="'dcapi-oid4vp-unsigned-' + profile.name">Unsigned OpenID4VP</label>
                        </div>
                    </fieldset>
                    <fieldset v-if="dcapiSelection.oid4vpMode === 'SIGNED' || dcapiSelection.oid4vpMode === 'MULTISIGNED'"
                              class="text-start mb-2">
                        <legend class="fs-6 fw-bold">Verifier identities</legend>
                        <p class="small text-muted mb-1">Every selected identity signs the same OpenID4VP transaction.</p>
                        <div class="form-check" v-for="signer in profile.dcApiSigners"
                             :key="dcapiSelection.oid4vpMode + '-' + signer.id">
                            <input class="form-check-input"
                                   :type="dcapiSelection.oid4vpMode === 'SIGNED' ? 'radio' : 'checkbox'"
                                   :id="'dcapi-signer-' + profile.name + '-' + signer.id"
                                   :name="dcapiSelection.oid4vpMode === 'SIGNED' ? 'dcapi-signer-' + profile.name : null"
                                   :checked="effectiveSignerIds().includes(signer.id)"
                                   @change="selectSigner(signer.id, $event.target.checked)">
                            <label class="form-check-label" :for="'dcapi-signer-' + profile.name + '-' + signer.id">
                                {{ signer.label }} <code>{{ signer.scheme }}</code>
                                <span v-if="signer.wrpac" class="badge text-bg-secondary ms-1">WRPAC</span>
                            </label>
                            <div v-if="effectiveSignerIds().includes(signer.id)" class="form-check form-check-inline ms-2">
                                <input class="form-check-input" type="checkbox"
                                       :id="'dcapi-forge-' + profile.name + '-' + signer.id"
                                       :checked="isForged(signer.id)"
                                       @change="selectForgedSigner(signer.id, $event.target.checked)">
                                <label class="form-check-label small text-danger"
                                       :for="'dcapi-forge-' + profile.name + '-' + signer.id">Forge signature</label>
                            </div>
                            <div v-if="effectiveSignerIds().includes(signer.id) && profile.dcApiRegistrationCertificates?.length"
                                 class="d-flex align-items-center gap-2 mt-1 mb-2">
                                <label class="small text-nowrap" :for="'dcapi-wrprc-' + profile.name + '-' + signer.id">
                                    Registration certificate
                                </label>
                                <select class="form-select form-select-sm"
                                        :id="'dcapi-wrprc-' + profile.name + '-' + signer.id"
                                        :value="signerWrprcId(profile, signer)"
                                        @change="selectSignerWrprc(signer.id, $event.target.value)">
                                    <option value="">None</option>
                                    <option v-for="wrprc in profile.dcApiRegistrationCertificates"
                                            :key="wrprc.id" :value="wrprc.id">{{ wrprc.label }}</option>
                                </select>
                                <span v-if="isMismatchedWrprc(profile, signer.id)"
                                      class="small text-danger text-nowrap">Mismatched</span>
                            </div>
                        </div>
                        <div v-if="profile.dcApiRegistrationCertificates?.length" class="form-check mt-1">
                            <input class="form-check-input" type="checkbox"
                                   :id="'dcapi-allow-mismatch-' + profile.name"
                                   :checked="dcapiSelection.allowMismatchedVerifierInfo === true"
                                   @change="selectAllowMismatchedVerifierInfo($event.target.checked)">
                            <label class="form-check-label small text-danger" :for="'dcapi-allow-mismatch-' + profile.name">
                                Allow mismatched registration certificates (for testing)
                            </label>
                        </div>
                        <p class="small text-muted mb-0">
                            Testing only: a forged signature keeps the identity's protected header, but its value does
                            not verify.
                        </p>
                        <div v-for="message in dcApiSelectionErrors(profile)" :key="message"
                             class="small text-danger">{{ message }}</div>
                    </fieldset>
                    <div v-if="isoMdocRequest" class="form-check text-start">
                        <input class="form-check-input" type="checkbox" :id="'dcapi-iso-' + profile.name"
                               :checked="dcapiSelection.isoMdoc === true"
                               @change="$emit('update:dcapiSelection', { ...dcapiSelection, isoMdoc: $event.target.checked })">
                        <label class="form-check-label" :for="'dcapi-iso-' + profile.name">ISO 18013-7 Annex C</label>
                    </div>
                    <div class="form-check text-start">
                        <input class="form-check-input" type="checkbox" :id="'dcapi-encrypt-' + profile.name"
                               :checked="dcapiSelection.encrypt !== false"
                               @change="$emit('update:dcapiSelection', { ...dcapiSelection, encrypt: $event.target.checked })">
                        <label class="form-check-label" :for="'dcapi-encrypt-' + profile.name">Encrypt response (OpenID4VP)</label>
                    </div>
                    <div class="text-center mt-3">
                        <button @click="$emit('invokeDCAPI', profile.dcApiUrl || profile.url, dcapiSelection)"
                                :disabled="!canStartDcApi(profile)"
                                class="btn btn-primary">Start Request</button>
                    </div>
                    </template>
                </div>
                
                </div>
            </div>
        </div>
    </div>
</div>
`
}
