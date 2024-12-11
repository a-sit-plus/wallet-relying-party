export default {
    props: [ 'error' ],
    template: `
<div v-if="error.message" class="row alert alert-danger" role="alert">
    {{ error.message }}
</div>
`
}
