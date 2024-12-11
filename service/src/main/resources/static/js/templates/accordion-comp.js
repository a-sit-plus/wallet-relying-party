export default {
    props: [ 'title' ],
    template: `
<div class="mb-3 accordion" id="accordionRequest">
    <div class="accordion-item">
        <h2 class="accordion-header" id="headingOne">
            <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse"
                    data-bs-target="#collapseOne" aria-expanded="true" aria-controls="collapseOne">
                {{ title }}
            </button>
        </h2>
        <div id="collapseOne" class="accordion-collapse collapse" aria-labelledby="headingOne"
             data-bs-parent="#accordionRequest">
            <div class="accordion-body">
                <slot />
            </div>
        </div>
    </div>
</div>
`
}
