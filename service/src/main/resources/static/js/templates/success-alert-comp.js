export default {
    props: [ 'request' ],
    template: `
<div v-if="request != null && request.qrCodeSrc != null" class="row alert alert-success mb-3" role="alert">
    <p>Request has been generated successfully!</p>
</div>
`
}
