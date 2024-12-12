export default {
    props: {
        title: { default: "A-SIT Demo Wallet Service Provider" },
        darkMode: { default: false }
    },
    template: `
<header class="p-3" :class="darkMode ? 'text-bg-dark' : 'text-bg-primary'">
  <div class="container">
    <div class="d-flex flex-wrap align-items-center justify-content-center justify-content-lg-start d-flex mb-2 mb-lg-0 me-md-auto text-white fs-3 fw-bold">
      <a class="link-light text-decoration-none" href="index.html">{{ title }}</a>
      <div class="ms-auto btn btn-light fs-4">
        <a href="https://wallet.a-sit.at/"><img style="height: 1em" src="images/ASIT Logo.png"/></a>
      </div>
    </div>
  </div>
</header>
`
}
