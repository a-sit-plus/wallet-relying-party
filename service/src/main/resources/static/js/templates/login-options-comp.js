export default {
    props: [
        'result',
        'changed',
        'error'
    ],
    emits: [
        'generateQrCode'
    ],
    template: `
<div v-if="result != null && result.qrCodes != null && changed.changed && !error.message" class="z-3 position-absolute rounded w-100">
    <div class="card w-50 mx-auto mt-5 text-center">
        <div class="card-body">
            <h5 class="card-title">Request Data Changed</h5>
            <p class="card-text">You have changed the request data. Please refresh the QR code and online wallet
                link.</p>
            <button @click="$emit('generateQrCode')" class="btn btn-primary">Refresh Request</button>
        </div>
    </div>
</div>

<div v-if="result != null && result.qrCodes != null"
     class="card mb-3"
     :class="{ 'blur' : changed.changed}">

    <div class="card-header">
        <ul class="nav nav-tabs card-header-tabs" role="tablist">
            <li class="nav-item" v-for="(qrCode, index) in result.qrCodes">
                <button class="nav-link" data-bs-toggle="tab"
                        :data-bs-target="'#tab-' + qrCode.name"
                        :class="{ 'active' : index == 0}"
                        type="button">
                    {{ qrCode.name }}
                </button>
            </li>
        </ul>
    </div>
    <div class="card-body tab-content container">
        <div v-for="(qrCode, index) in result.qrCodes"
             class="tab-pane"
             role="tabpanel"
             :id="'tab-' + qrCode.name"
             :class="{ 'active' : index == 0}">
            <div class="row">
            <div class="col-lg-4 border rounded p-2 bg-white">
                <h2>Option A: Cross device</h2>
                <p>Scan the QR code with your Wallet App:</p>
                <div class="text-left">
                    <a target="_blank" :href="qrCode.url">
                        <img width="300px" :src="qrCode.png"/>
                    </a>
                </div>
            </div>
            <div class="col-lg-4 border rounded p-2 bg-white">
                <h2>Option B: Same device</h2>
                <p>Click the following button to open the Wallet App on this device:</p>
                <div class="text-center">
                    <a target="_blank" :href="qrCode.url" class="btn btn-primary m-3">Open App Wallet</a>
                </div>
                <p>The whole link is: <a target="_blank" :href="qrCode.url">{{ qrCode.url }}</a></p>
            </div>
            
            <div class="col-lg-4 border rounded p-2 bg-white">
                <h2>Option C: Remote Wallet</h2>
                <p>Click the following button to authenticate via the Remote Wallet:</p>
                <div class="text-center">
                    <a target="_blank" :href="result.remoteWalletUrl" class="btn btn-primary mb-3"
                       :title="result.remoteWalletUrl">Open Remote Wallet</a>
                </div>
            </div>
            </div>
        </div>
    </div>
</div>
`
}
