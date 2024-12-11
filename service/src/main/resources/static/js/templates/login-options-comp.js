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
             class="tab-pane row"
             role="tabpanel"
             :id="'tab-' + qrCode.name"
             :class="{ 'active' : index == 0}">
            <p>Either scan the QR code with your mobile app or click the link to open your Wallet</p>
            <div class="text-left">
                <a target="_blank" :href="qrCode.url">
                    <img width="300px" :src="qrCode.png"/>
                </a>
            </div>
            <p>The whole link is: <a target="_blank" :href="qrCode.url">{{ qrCode.url }}</a></p>
            <div class="text-center">
                <a target="_blank" :href="result.remoteWalletUrl" class="btn btn-primary mb-3"
                   :title="result.remoteWalletUrl">Open Remote Wallet</a>
            </div>
        </div>
    </div>
</div>
`
}
