import {ref} from 'vue'

export default {
    props: [
        'title',
        'icon'
    ],
    setup() { },
    template: `
<div class="row">
    <h3 class="fs-2 text-body-emphasis">
        <i width="1em" height="1em" :class="icon"></i>
        {{ title }}
    </h3>
    <slot />
</div>
`
}
