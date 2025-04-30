export default {
    props: [
        'result',
        'changed',
        'error'
    ],
    emits: [
        'generateQrCode',
        'invokeDCAPI'
    ],
    template: `
<div v-if="result != null && result.profiles != null && changed.changed && !error.message" class="z-3 position-absolute rounded w-100">
    <div class="card w-50 mx-auto mt-5 text-center">
        <div class="card-body">
            <h5 class="card-title">Request Data Changed</h5>
            <p class="card-text">You have changed the request data. Please refresh the QR code and online wallet
                link.</p>
            <button @click="$emit('generateQrCode')" class="btn btn-primary">Refresh Request</button>
        </div>
    </div>
</div>

<div v-if="result != null && result.profiles != null"
     class="card mb-3"
     :class="{ 'blur' : changed.changed}">

    <div class="card-header">
        <ul class="nav nav-tabs card-header-tabs" role="tablist">
            <li class="nav-item" v-for="(profile, index) in result.profiles">
                <button class="nav-link" data-bs-toggle="tab"
                        :data-bs-target="'#tab-' + profile.name"
                        :class="{ 'active' : index == 0}"
                        type="button">
                    {{ profile.label }}
                </button>
            </li>
        </ul>
    </div>
    <div class="card-body tab-content container">
        <div v-for="(profile, index) in result.profiles"
             class="tab-pane"
             role="tabpanel"
             :id="'tab-' + profile.name"
             :class="{ 'active' : index == 0}">
            <div class="row">
            <div class="col-lg-4 border rounded p-2 bg-white">
                <h2>Option A: Cross device</h2>
                <p>Scan the QR code with your Wallet App:</p>
                <div class="text-left">
                    <a target="_blank" :href="profile.url">
                        <img width="300px" :src="profile.png"/>
                    </a>
                </div>
            </div>
            <div class="col-lg-4 border rounded p-2 bg-white">
                <h2>Option B: Same device</h2>
                <p>Click the following button to open the Wallet App on this device:</p>
                <div class="text-center">
                    <a target="_blank" :href="profile.url" class="btn btn-primary m-3">Open App Wallet</a>
                </div>
                <p>The whole link is: <a target="_blank" :href="profile.url">{{ profile.url }}</a></p>
                <p>Click the following button to authenticate via the Remote Wallet:</p>
                <div class="text-center">
                    <a target="_blank" :href="profile.remoteWalletUrl" class="btn btn-primary mb-3"
                       :title="profile.remoteWalletUrl">Open Remote Wallet</a>
                </div>
            </div>
            <div class="col-lg-4 border rounded p-2 bg-white">
                <h2>Option C: Digital Credentials API</h2>
                <p>Click the following button to authenticate via the Digital Credentials API:</p>
                <div class="text-center">
                    <button @click="$emit('invokeDCAPI')" class="btn btn-primary">Start Request</button>
                </div>
            </div>
            </div>
        </div>
    </div>
</div>
`
}
