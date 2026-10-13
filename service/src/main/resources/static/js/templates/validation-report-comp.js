// One node of the validation report of a wallet response (see ValidationNodeView), with its checks and, nested,
// its children: the presentations of a response, the credentials of a presentation, the status lists of a credential.
// Text is only interpolated, never rendered as HTML, as subjects and details may come from the wallet.
export default {
    name: 'ValidationReportComp',
    props: {
        node: { type: Object, required: true },
    },
    setup() {
        const outcomes = {
            PASSED: { icon: 'bi-check-circle-fill text-success', text: 'passed' },
            FAILED: { icon: 'bi-x-circle-fill text-danger', text: 'failed' },
            BLOCKED: { icon: 'bi-slash-circle-fill text-warning', text: 'blocked' },
            NOT_APPLICABLE: { icon: 'bi-dash-circle text-secondary', text: 'not applicable' },
        }

        function outcomeOf(check) {
            return outcomes[check.outcome] || { icon: 'bi-question-circle', text: check.outcome }
        }

        // Whether the check rejects the artifact, or did not pass but the policy tolerates it.
        function badgeOf(check) {
            if (!check.satisfied) return { cls: 'text-bg-danger', text: 'rejects' }
            if (check.outcome === 'FAILED' || check.outcome === 'BLOCKED')
                return { cls: 'text-bg-warning', text: 'tolerated by policy' }
            return null
        }

        function isAccepted(node) {
            return node.decision === 'ACCEPTED'
        }

        return { outcomeOf, badgeOf, isAccepted }
    },
    template: `
<div class="card mb-2 validation-node" :class="isAccepted(node) ? 'border-success' : 'border-danger'">
  <div class="card-header d-flex flex-wrap align-items-center gap-2">
    <i class="bi fs-5" :class="isAccepted(node) ? 'bi-shield-check text-success' : 'bi-shield-x text-danger'"></i>
    <strong class="me-auto text-break">{{ node.title }}</strong>
    <span class="badge" :class="isAccepted(node) ? 'text-bg-success' : 'text-bg-danger'">
      {{ isAccepted(node) ? 'Accepted' : 'Rejected' }}
    </span>
    <small class="text-body-secondary">{{ node.evaluatedAt }}</small>
  </div>
  <div class="card-body p-2">
    <div class="table-responsive">
      <table class="table table-sm align-middle mb-2 validation-checks">
        <tbody>
          <tr v-for="check in node.checks" :key="check.name" :class="{ 'table-danger': !check.satisfied }">
            <td class="validation-icon">
              <i class="bi" :class="outcomeOf(check).icon" :title="outcomeOf(check).text"></i>
            </td>
            <td class="fw-semibold validation-label">{{ check.label }}</td>
            <td class="text-break">
              <div v-if="check.detail">{{ check.detail }}</div>
              <div v-if="check.subject" class="small text-body-secondary">{{ check.subject }}</div>
              <div v-if="!check.detail && !check.subject" class="small text-body-secondary">
                {{ outcomeOf(check).text }}
              </div>
            </td>
            <td class="text-end">
              <span v-if="badgeOf(check)" class="badge" :class="badgeOf(check).cls">{{ badgeOf(check).text }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="node.children && node.children.length" class="validation-children">
      <validation-report-comp v-for="(child, index) in node.children" :key="index" :node="child">
      </validation-report-comp>
    </div>
  </div>
</div>
`
}
