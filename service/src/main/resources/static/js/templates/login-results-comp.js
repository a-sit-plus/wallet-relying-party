export default {
    props: {
        loginList: {},
        expandable: { default: false },
        removable: { default: false }
    },
    emits: [
        'toggleDetails',
        'seen'
    ],
    setup: function () {
        function filterClaims(item) {
            const deepCopy = JSON.parse(JSON.stringify(item))
            delete deepCopy.timestamp
            delete deepCopy.credentials
            delete deepCopy.expiredTime
            delete deepCopy.imageDataBase64
            delete deepCopy.showDetails
            delete deepCopy.idToken
            delete deepCopy.idTokenError
            delete deepCopy.presentationError
            return deepCopy
        }

        function isSet(value) {
            return value && value != "N/A" && value != "data:image;base64,null"
        }

        return {
            isSet,
            filterClaims,
        }
    },
    template: `
<div>
  <p v-if="loginList.length == 0">There are no recent authentication results.</p>

  <transition-group tag="div" name="fade" class="row mb-2">

      <div v-for="item in loginList" :key="item.id" class="col-md-6">
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

                      <h3 v-if="isSet(item.firstname) || isSet(item.lastname)" class="mb-1">
                          {{ item.firstname }} {{ item.lastname }}
                      </h3>

                      <p v-for="(value, key) in filterClaims(item)" :key="key" class="text-truncate mb-1"
                         style="max-width: 100%;">
                          <span class="fw-semibold">{{ key }}</span>: {{ value }}
                      </p>

                      <div v-for="(credential, key) in item.credentials" :key="key"
                           class="border rounded p-2 bg-light my-3">
                          <h4 v-if="credential.credentialType != null">Credential Type: {{ credential.credentialType }}</h4>
                          <p v-for="(value, key) in credential.allFields" :key="key"
                             class="text-break mb-1">
                              <span class="fw-semibold">{{ key }}: </span>
                              <span v-if="key == 'portrait' || key == 'signature_usual_mark'"
                                    class="text-truncate d-inline-block" style="max-width: 100%">{{ value }}</span>
                              <span v-else>{{ value }}</span>
                          </p>
                          <div v-if="credential.error != null">
                              <h4>Error</h4>
                              <p><span class="row alert alert-danger" role="alert">{{ credential.error }}</span></p>
                          </div>
                      </div>
                      <div class="foldout-fade"></div>
                  </div>
                  <div class="text-body-tertiary mb-1">Authenticated {{ item.expiredTime }} ago</div>
                  <div v-if="removable" class="mt-auto ms-auto d-md-flex gap-2">
                      <button @click="seen(item)" type="button" class="btn btn-outline-danger">
                          <i class="bi-x-lg"></i>
                          Remove
                      </button>
                  </div>
              </div>
          </div>
      </div>

  </transition-group>
</div>
`
}
