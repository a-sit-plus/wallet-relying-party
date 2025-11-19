export default {
    props: [ 'title', 'icon', 'buttonText', 'buttonHref' ],
    template: `
<div class="card">
  <div class="card-body">
    <h3 class="card-title">
        <i width="1em" height="1em" :class="icon"></i>
        {{ title }}
    </h3>
    <p class="card-text">
      <slot />
    </p>
    <a :href="buttonHref" class="btn btn-primary float-end">
      {{ buttonText }}
      <i class="bi-box-arrow-up-right"></i>
    </a>
  </div>
</div>
`
}
