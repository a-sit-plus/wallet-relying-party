export default {
    props: [ 'title' ],
    data() {
        return { uid: Math.random().toString(36).substring(2, 9) }
    },
    template: `
<div class="mb-3 accordion" :id="'accordionRequest-' + uid">
    <div class="accordion-item">
        <h2 class="accordion-header" :id="'heading-' + uid">
            <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse"
                    :data-bs-target="'#collapse-' + uid" aria-expanded="true"
                    :aria-controls="'collapse-' + uid">
                {{ title }}
            </button>
        </h2>
        <div :id="'collapse-' + uid" class="accordion-collapse collapse" :aria-labelledby="'heading-' + uid"
             :data-bs-parent="'#accordionRequest-' + uid">
            <div class="accordion-body">
                <slot />
            </div>
        </div>
    </div>
</div>
`
}
