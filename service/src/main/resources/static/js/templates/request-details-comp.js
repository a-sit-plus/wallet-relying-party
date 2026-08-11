import AttributeSelectComp from './attribute-select-comp.js'

export default {
    props: {
        request: {},
        config: {},
        wrpacPreview: { default: null },
        wrprcPreviews: { default: null },
        showCertificates: { default: false },
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
        'updateIncludeWrpac',
        'updateSelectedWrprc',
        'updateAttribute',
        'addCredential',
        'removeCredential',
        'generateQrCode'
    ],
    computed: {
        hasWrpacPreview() {
            return !!(this.wrpacPreview && typeof this.wrpacPreview === 'object' && this.wrpacPreview.content)
        },
        wrprcPreviewList() {
            return Array.isArray(this.wrprcPreviews) ? this.wrprcPreviews.filter(item => item && typeof item === 'object') : []
        },
        wrprcPreviewsWithSummary() {
            return this.wrprcPreviewList.map(item => ({
                ...item,
                summary: this.wrprcSummary(item)
            }))
        }
    },
    methods: {
        wrprcSummary(item) {
            const payload = this.parseJwsPayload(item?.content)
            if (!payload || typeof payload !== 'object') {
                return null
            }

            const credentials = Array.isArray(payload.credentials) ? payload.credentials : []
            const claims = credentials.flatMap(credential => {
                const entries = Array.isArray(credential.claim) ? credential.claim : []
                return entries
                    .map(entry => Array.isArray(entry.path) ? entry.path.join(" / ") : null)
                    .filter(Boolean)
            })

            const purposes = Array.isArray(payload.purpose)
                ? payload.purpose
                    .map(entry => entry?.value)
                    .filter(value => typeof value === 'string' && value.trim().length > 0)
                : []

            return {
                service: this.firstLocalizedValue(payload.srv_description),
                credentials: credentials
                    .map(credential => {
                        const format = credential?.format || null
                        const doctype = credential?.meta?.doctype_value || null
                        const vct = Array.isArray(credential?.meta?.vct_values) ? credential.meta.vct_values.join(", ") : null
                        return [format, doctype || vct].filter(Boolean).join(" - ")
                    })
                    .filter(Boolean),
                claims,
                purposes
            }
        },
        parseJwsPayload(compactJws) {
            if (typeof compactJws !== 'string') return null
            const parts = compactJws.split('.')
            if (parts.length < 2) return null
            try {
                const normalized = parts[1]
                    .replace(/-/g, '+')
                    .replace(/_/g, '/')
                const padded = normalized + '='.repeat((4 - normalized.length % 4) % 4)
                const decoded = atob(padded)
                const bytes = Uint8Array.from(decoded, char => char.charCodeAt(0))
                return JSON.parse(new TextDecoder().decode(bytes))
            } catch (error) {
                console.warn('Failed to parse WRPRC payload', error)
                return null
            }
        },
        firstLocalizedValue(value) {
            if (!Array.isArray(value)) return null
            for (const group of value) {
                if (!Array.isArray(group)) continue
                for (const entry of group) {
                    if (typeof entry?.value === 'string' && entry.value.trim().length > 0) {
                        return entry.value
                    }
                    if (typeof entry?.content === 'string' && entry.content.trim().length > 0) {
                        return entry.content
                    }
                }
            }
            return null
        },
        certificateInputId(prefix, id = '') {
            const suffix = String(id)
                .replace(/[^a-zA-Z0-9_-]/g, '-')
                .replace(/-+/g, '-')
                .replace(/^-|-$/g, '')
            return suffix ? `${prefix}-${suffix}` : prefix
        }
    },
    template: `


<div class="row pt-2" v-if="showCertificates">
    <legend class="">
        Verifier Certificates
        <a class="btn btn-light" href="/certificates.html">
            <i class="bi-eye"></i>
        </a>
    </legend>
    <div :class="singleColumn ? '' : 'col-lg-7 overflow-hidden'">
        <fieldset class="row mb-3">
            <legend class="col-form-label col-sm-4 pt-0 fw-bold">Add Certificates to Request</legend>
            <div class="col-sm-8">
                <div class="form-check">
                    <input class="form-check-input"
                           id="includeWrpac"
                           type="checkbox"
                           name="includeWrpac"
                           :disabled="!hasWrpacPreview"
                           :checked="request.includeWrpac"
                           @click="$emit('updateIncludeWrpac', !request.includeWrpac)">
                    <label class="form-check-label" for="includeWrpac">
                        WRP Access Certificate (WRPAC) - <span class="text-primary">x509_hash / x5c</span>
                        <span v-if="!hasWrpacPreview" class="text-muted">(not available)</span>
                    </label>
                </div>
                <div class="mt-3">
                    <label class="form-label mb-1">
                        WRP Registration Certificate (WRPRC) - <span class="text-primary text-nowrap">verifier_info / registration_cert</span>
                    </label>
                    <div v-if="wrprcPreviewsWithSummary.length === 0" class="text-muted small">(not available)</div>
                    <div v-else class="form-check">
                        <input class="form-check-input"
                               id="selectedWrprcId-none"
                               type="radio"
                               name="selectedWrprcId"
                               :checked="!request.selectedWrprcId"
                               @click="$emit('updateSelectedWrprc', null)">
                        <label class="form-check-label" for="selectedWrprcId-none">
                            Do not include WRPRC
                        </label>
                    </div>
                    <div v-for="item in wrprcPreviewsWithSummary"
                         :key="item.id"
                         class="form-check mb-2">
                        <input class="form-check-input"
                               type="radio"
                               :id="certificateInputId('selectedWrprcId', item.id)"
                               name="selectedWrprcId"
                               :value="item.id"
                               :checked="request.selectedWrprcId === item.id"
                               @click="$emit('updateSelectedWrprc', item.id)">
                        <label class="form-check-label" :for="certificateInputId('selectedWrprcId', item.id)">
                            {{ item.label }}
                        </label>
                        <div v-if="item.summary" class="small text-muted mt-1 ms-4">
                            <div v-if="item.summary.service">
                                Service: {{ item.summary.service }}
                            </div>
                            <div v-if="item.summary.credentials.length > 0">
                                Credentials: {{ item.summary.credentials.join("; ") }}
                            </div>
                            <div v-if="item.summary.claims.length > 0">
                                Claims: {{ item.summary.claims.join(", ") }}
                            </div>
                            <div v-if="item.summary.purposes.length > 0">
                                Purpose: {{ item.summary.purposes[0] }}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </fieldset>
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
