export default {
    props: [ 'reqResult' ],
    template: `
<div v-if="reqResult != null && reqResult.qrCodeSrc != null" class="row alert alert-success mb-3" role="alert">
    <p>Request has been generated successfully!</p>
</div>
`
}
