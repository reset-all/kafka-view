<template>
  <div class="json-viewer">
    <div v-if="isPrimitive">
      <span class="json-key" v-if="name" v-html="highlightedName"></span>
      <span v-if="name">: </span>
      <span :class="['json-value', type]" v-html="highlightedValue"></span>
    </div>
    <div v-else>
      <div class="json-expandable" @click="toggle">
        <span class="json-toggle-icon">{{ expanded ? '▼' : '▶' }}</span>
        <span class="json-key" v-if="name" v-html="highlightedName"></span>
        <span v-if="name">: </span>
        <span class="json-summary">{{ summary }}</span>
      </div>
      <div v-if="expanded" class="json-children">
        <json-viewer
          v-for="(value, key) in data"
          :key="key"
          :name="isArray ? '' : key"
          :data="value"
          :depth="depth + 1"
          :expand-signal="expandSignal"
          :highlight-text="highlightText"
        />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'JsonViewer'
}
</script>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  data: { required: true },
  name: { type: [String, Number], default: '' },
  depth: { type: Number, default: 0 },
  expandSignal: { type: Number, default: 0 },
  highlightText: { type: String, default: '' }
})

const expanded = ref(props.depth < 2) // Auto expand first 2 levels

watch(() => props.expandSignal, (val) => {
  if (val > 0) expanded.value = true
  if (val < 0) expanded.value = false
})

// Auto-expand if children contain highlight text
watch(() => props.highlightText, (val) => {
  if (val && !isPrimitive.value) {
    // Simple check: if stringified data contains keyword, expand.
    // This might be expensive for huge JSON, but effective.
    try {
      if (JSON.stringify(props.data).includes(val)) {
        expanded.value = true
      }
    } catch (e) {}
  }
})

const type = computed(() => {
  if (props.data === null) return 'null'
  if (Array.isArray(props.data)) return 'array'
  return typeof props.data
})

const isPrimitive = computed(() => {
  return !['array', 'object'].includes(type.value) || props.data === null
})

const isArray = computed(() => type.value === 'array')

const highlight = (text) => {
  if (!props.highlightText || typeof text !== 'string') return text
  // Escape regex special characters in keyword
  const escapedKeyword = props.highlightText.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${escapedKeyword})`, 'gi')
  return text.replace(regex, '<span class="highlight-match">$1</span>')
}

const highlightedName = computed(() => {
  if (!props.name) return ''
  return highlight(String(props.name))
})

const highlightedValue = computed(() => {
  if (props.data === null) return 'null'
  let val = props.data
  if (typeof props.data === 'string') val = `"${props.data}"`
  return highlight(String(val))
})

const summary = computed(() => {
  if (isArray.value) {
    return `Array(${props.data.length})`
  } else {
    return `Object{${Object.keys(props.data).length}}`
  }
})

const toggle = () => {
  expanded.value = !expanded.value
}
</script>

<style>
.highlight-match {
  background-color: #ffeb3b;
  color: #000;
  font-weight: bold;
  border-radius: 2px;
}
</style>

<style scoped>
.json-viewer {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
}
.json-key {
  color: #881391;
  font-weight: bold;
  margin-right: 4px;
}
.json-value.string { color: #c41a16; }
.json-value.number { color: #1c00cf; }
.json-value.boolean { color: #0d904f; }
.json-value.null { color: #808080; }

.json-expandable {
  cursor: pointer;
  display: flex;
  align-items: center;
  user-select: none;
}
.json-expandable:hover {
  background-color: #f5f5f5;
}
.json-toggle-icon {
  font-size: 10px;
  margin-right: 5px;
  color: #666;
  width: 12px;
  display: inline-block;
  text-align: center;
}
.json-summary {
  color: #999;
  font-style: italic;
  margin-left: 5px;
}
.json-children {
  padding-left: 20px;
  border-left: 1px solid #eee;
  margin-left: 6px;
}
</style>
