export default {
    props: [
        'result',
        'changed',
        'error',
        'dcapiSelection'
    ],
    data() {
        return {
            activeProfileName: null
        }
    },
    computed: {
        regularProfiles() {
            return (this.result?.profiles || []).filter(profile => !this.isDcApiProfile(profile))
        },
        dcApiProfiles() {
            return (this.result?.profiles || []).filter(profile => this.isDcApiProfile(profile))
        }
    },
    emits: [
        'generateQrCode',
        'invokeDCAPI',
        'profileTabChanged',
        'update:dcapiSelection'
    ],
    methods: {
        isDcApiProfile(profile) {
            return typeof profile?.label === 'string' && profile.label.startsWith('DCAPI: ')
        },
        activateProfile(profileName) {
            this.activeProfileName = profileName
            this.$emit('profileTabChanged', profileName)
        },
        ensureActiveProfile() {
            const profiles = this.result?.profiles || []
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
        <ul v-if="regularProfiles.length > 0" class="nav nav-tabs card-header-tabs" role="tablist">
            <li class="nav-item" v-for="profile in regularProfiles">
                <button class="nav-link" data-bs-toggle="tab"
                        :data-bs-target="'#tab-' + profile.name"
                        :class="{ 'active' : activeProfileName === profile.name}"
                        @click="activateProfile(profile.name)"
                        type="button">
                    {{ profile.label }}
                </button>
            </li>
        </ul>
        <ul v-if="dcApiProfiles.length > 0" class="nav nav-tabs card-header-tabs mt-2" role="tablist">
            <li class="nav-item" v-for="profile in dcApiProfiles">
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
            <div v-for="profile in result.profiles"
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
                
                <div v-if="profile.supportedOptions.includes('OID4VP_DC_API') || profile.supportedOptions.includes('ISO_MDOC_DC_API')" class="option-card border rounded p-2 bg-white">
                    <h2>Digital Credentials API</h2>
                    <p v-if="profile.supportedOptions.includes('OID4VP_DC_API')">Select the OpenID4VP mode and start the request:</p>
                    <p v-else>This profile uses ISO 18013-7 via the Digital Credentials API.</p>
                    <div v-if="profile.supportedOptions.includes('OID4VP_DC_API')">
                        <div class="form-check text-start">
                            <input class="form-check-input" type="radio" :id="'dcapi-unsigned-' + profile.name"
                                   :name="'dcapi-oid4vp-' + profile.name"
                                   :checked="dcapiSelection.signedOid4vp === false"
                                   @change="$emit('update:dcapiSelection', { ...dcapiSelection, signedOid4vp: false })">
                            <label class="form-check-label" :for="'dcapi-unsigned-' + profile.name">Unsigned OpenID4VP</label>
                        </div>
                        <div class="form-check text-start">
                            <input class="form-check-input" type="radio" :id="'dcapi-signed-' + profile.name"
                                   :name="'dcapi-oid4vp-' + profile.name"
                                   :checked="dcapiSelection.signedOid4vp === true"
                                   @change="$emit('update:dcapiSelection', { ...dcapiSelection, signedOid4vp: true })">
                            <label class="form-check-label" :for="'dcapi-signed-' + profile.name">Signed OpenID4VP</label>
                        </div>
                    </div>
                    <div class="text-center mt-3">
                        <button @click="$emit('invokeDCAPI', profile.dcApiUrl || profile.url, dcapiSelection)" class="btn btn-primary">Start Request</button>
                    </div>
                </div>
                
                </div>
            </div>
        </div>
    </div>
</div>
`
}
