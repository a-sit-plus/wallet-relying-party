export default {
    props: {
        loginList: {},
        config: { default: () => ({ schemeTypes: [] }) },
        expandable: { default: false }
    },
    emits: [
        'toggleDetails'
    ],
    setup: function (props) {
        function isSet(value) {
            return value && value != "data:image;base64,null"
        }

        // Type metadata for the credential, matched by credentialType (vct for SD-JWT, docType for ISO mdoc).
        function schemeFor(credential) {
            return props.config.schemeTypes?.find(s => s.value === credential.credentialType)
        }

        // Translated scheme name, falling back to the raw credentialType.
        function schemeLabel(credential) {
            return schemeFor(credential)?.label || credential.credentialType || 'Credential'
        }

        // Translated claim description, falling back to the raw claim key.
        function claimLabel(credential, key) {
            return schemeFor(credential)?.attributes?.find(a => a.value === key)?.label || key
        }

        // The reconstructed credential body: SD-JWT/ISO expose allFields, plain VC-JWS the credentialSubject.
        function claimsOf(credential) {
            return credential.allFields || credential.jwtCredential || {}
        }

        function hasClaims(credential) {
            return Object.keys(claimsOf(credential)).length > 0
        }

        function credentialJson(credential) {
            return JSON.stringify(claimsOf(credential), null, 2)
        }

        return {
            isSet,
            schemeLabel,
            claimLabel,
            claimsOf,
            hasClaims,
            credentialJson,
        }
    },
    template: `
<div>
  <p v-if="loginList.length == 0">There are no recent authentication results.</p>

  <transition-group tag="div" name="fade" class="row mb-2">

      <div v-for="item in loginList" :key="item.timestamp" class="col-md-6">
          <div class="row g-0 border rounded overflow-hidden flex-md-row mb-4 shadow-sm bg-white">
              <div v-if="item.idTokenError != null">
                  <p><span class="row alert alert-danger" role="alert">
                    <h4>Id token error</h4>
                    <span> {{ item.idTokenError }}</span>
                  </span></p>
              </div>
              <div v-if="item.presentationError != null">
                <p><span class="row alert alert-danger" role="alert">
                  <h4>Presentation error</h4>
                  <span> {{ item.presentationError }}</span>
                </span></p>
              </div>
              <div v-if="isSet(item.imageDataBase64)" class="col-lg-4 text-bg-dark" style="text-align: center">
                  <img style="max-height: 400px;max-width: 100%" :src="item.imageDataBase64"/>
              </div>
              <div :class="isSet(item.imageDataBase64) ? 'col-lg-8' : 'col-lg-12'"
                   class="p-4 d-flex flex-column position-static">
                  <div class="foldout mb-1" :class="(!expandable || item.showDetails) ? '' : 'foldout-preview'">
                      <div v-if="expandable && !item.showDetails" class="foldout-button">
                          <button type="button" class="btn btn-outline-secondary btn-sm"
                                  @click="toggleDetails(item)">
                              More Details
                              <i class="bi-caret-down-fill"></i>
                          </button>
                      </div>
                      <div v-if="expandable && item.showDetails" class="foldout-button">
                          <button type="button" class="btn btn-outline-secondary btn-sm"
                                  @click="toggleDetails(item)">
                              Less Details
                              <i class="bi-caret-up-fill"></i>
                          </button>
                      </div>

                      <div v-for="(credential, key) in item.credentials" :key="key"
                           class="border rounded p-2 bg-light my-3">
                          <h4>{{ schemeLabel(credential) }}</h4>
                          <div v-if="item.trustState === 'TRUSTED'"
                               class="alert alert-success d-flex align-items-center" role="alert">
                              <i class="bi bi-shield-check fs-3 me-3"></i>
                              <strong>Trusted Issuer</strong>
                          </div>
                          <div v-else-if="item.trustState === 'UNTRUSTED'"
                               class="alert alert-danger d-flex align-items-center" role="alert">
                              <i class="bi bi-shield-x fs-3 me-3"></i>
                              <strong>Untrusted Issuer</strong>
                          </div>
                          <div v-else class="alert alert-warning d-flex align-items-center" role="alert">
                              <i class="bi bi-exclamation-triangle fs-3 me-3"></i>
                              <strong>Trust Status Unknown</strong>
                          </div>
                          <p v-for="(value, claimKey) in claimsOf(credential)" :key="claimKey"
                             class="text-break mb-1">
                              <span class="fw-semibold">{{ claimLabel(credential, claimKey) }}: </span>
                              <span v-if="claimKey == 'portrait' || claimKey == 'signature_usual_mark'"
                                    class="text-truncate d-inline-block" style="max-width: 100%">{{ value }}</span>
                              <span v-else>{{ value }}</span>
                          </p>
                          <details v-if="hasClaims(credential)" class="mt-2">
                              <summary class="text-body-secondary" style="cursor: pointer">Credential details (JSON)</summary>
                              <pre class="border rounded bg-body-tertiary p-2 mt-2 mb-0"
                                   style="white-space: pre-wrap; word-break: break-word">{{ credentialJson(credential) }}</pre>
                          </details>
                          <div v-if="credential.error != null">
                              <h4>Error</h4>
                              <p><span class="row alert alert-danger" role="alert">{{ credential.error }}</span></p>
                          </div>
                      </div>
                      <div class="foldout-fade"></div>
                  </div>
                  <div class="text-body-tertiary mb-1">Authenticated {{ item.expiredTime }} ago</div>
              </div>
          </div>
      </div>

  </transition-group>
</div>
`
}
