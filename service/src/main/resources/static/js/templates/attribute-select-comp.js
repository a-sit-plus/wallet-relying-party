export default {
    props: {
        credential: {}
    },
    emits: [
        'updateAttribute'
    ],
    computed: {
        selectable() {
            return this.credential.sd == true
                || (this.credential.representation && this.credential.representation.value != 'SD_JWT')
        },
        readOnly() {
            return this.credential.sd == false
                && (this.credential.representation && this.credential.representation.value == 'SD_JWT')
        },
        // Groups flat, dot-notated attributes (e.g. "address.street_address") into a tree:
        // the attribute named like the prefix (e.g. "address") becomes the parent node,
        // dotted attributes become its children, labelled by their leaf name only.
        attributeTree() {
            const items = this.credential.attributes || []
            const prefixes = new Set(
                items.filter(item => item.value.includes('.'))
                    .map(item => item.value.split('.')[0])
            )
            const tree = []
            const groups = new Map()
            for (const item of items) {
                const dotIndex = item.value.indexOf('.')
                if (dotIndex < 0) {
                    const node = { item: item, label: item.label, children: [] }
                    if (prefixes.has(item.value)) {
                        groups.set(item.value, node)
                    }
                    tree.push(node)
                } else {
                    const prefix = item.value.slice(0, dotIndex)
                    let node = groups.get(prefix)
                    if (!node) {
                        // dotted attribute without a parent entry: render a plain group header
                        node = { item: null, label: prefix, children: [] }
                        groups.set(prefix, node)
                        tree.push(node)
                    }
                    node.children.push({ item: item, label: item.value.slice(dotIndex + 1) })
                }
            }
            return tree
        }
    },
    template: `
<p v-if="credential.attributes.length == 0">Please select a Credential Type.</p>
<template v-if="selectable">
    <div v-for="node in attributeTree"
         :key="credential.schemeType + '-' + node.label">
        <div v-if="node.item" class="form-check">
            <input class="attributes form-check-input"
                   type="checkbox"
                   :name="(credential.schemeType ? credential.schemeType.label : 'unknown') + '-attributes'"
                   :value="node.item.value"
                   :checked="node.item.isSelected"
                   @click="$emit('updateAttribute', node.item)">
            <label class="form-check-label">
                {{ node.item.label }}
            </label>
        </div>
        <div v-else class="form-check ps-0 fst-italic">
            {{ node.label }}
        </div>
        <div v-if="node.children.length > 0" class="ms-4">
            <div v-for="child in node.children"
                 :key="credential.schemeType + '-' + child.item.value"
                 class="form-check">
                <input class="attributes form-check-input"
                       type="checkbox"
                       :name="(credential.schemeType ? credential.schemeType.label : 'unknown') + '-attributes'"
                       :value="child.item.value"
                       :checked="child.item.isSelected"
                       @click="$emit('updateAttribute', child.item)">
                <label class="form-check-label">
                    {{ child.label }}
                </label>
            </div>
        </div>
    </div>
</template>
<div v-if="readOnly">
    <p>For this credential, attributes can not be selectively disclosed.</p>
    <ul class="list-group list-group-flush">
        <li class="list-group-item" v-for="node in attributeTree">
            {{ node.label }}
            <ul v-if="node.children.length > 0" class="list-unstyled ms-4 mb-0">
                <li v-for="child in node.children">{{ child.label }}</li>
            </ul>
        </li>
    </ul>
</div>
`
}
