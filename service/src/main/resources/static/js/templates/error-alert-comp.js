export default {
    props: [ 'error' ],
    template: `
<div v-if="error.message" id="error-alert" class="row alert alert-danger" role="alert">
    {{ error.message }}
</div>
`
}
