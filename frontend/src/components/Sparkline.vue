<template>
  <div :style="rootStyle">
    <!-- Lightweight SVG sparkline when not using ECharts -->
    <svg v-if="!useEcharts" :width="svgWidth" :height="svgHeight" viewBox="0 0 140 36" preserveAspectRatio="none" @click="emitOpen" style="cursor: zoom-in;">
      <defs>
        <linearGradient id="g" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stop-color="#4F8DFF" stop-opacity="0.18" />
          <stop offset="100%" stop-color="#4F8DFF" stop-opacity="0" />
        </linearGradient>
      </defs>
      <path :d="areaPath" fill="url(#g)" stroke="none" />
      <path :d="linePath" fill="none" :stroke="strokeColor" stroke-width="1.6" />
      <g v-if="points.length">
        <circle v-for="(p, idx) in points" :key="idx" :cx="p.x" :cy="p.y" r="2.2" :fill="strokeColor" stroke="#fff" stroke-width="0.6">
          <title>{{ tooltipText(idx) }}</title>
        </circle>
      </g>
    </svg>

    <!-- ECharts container, only created when useEcharts is true -->
    <div v-if="useEcharts" ref="chartRoot" :style="Object.assign({}, rootStyle, { cursor: 'zoom-in' })"></div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, watch, ref } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  labels: { type: Array, default: () => [] }, // optional date labels oldest->newest
  // allow numeric px values or CSS width strings like '100%'
  width: { type: [Number, String], default: 140 },
  height: { type: [Number, String], default: 36 },
  strokeColor: { type: String, default: '#4F8DFF' },
  useEcharts: { type: Boolean, default: false },
  showAxes: { type: Boolean, default: false }
})

const emit = defineEmits(['open'])

// Lightweight SVG path builder for inline sparkline
const padding = { left: 4, right: 4, top: 4, bottom: 4 }
function buildPaths(arr) {
  const w = 140 - padding.left - padding.right
  const h = 36 - padding.top - padding.bottom
  if (!arr || arr.length === 0) {
    return { line: '', area: '', points: [] }
  }

  // Normalize values: keep nulls as gaps, convert others to numbers
  const numeric = arr.map(v => (v === null || v === undefined) ? null : Number(v))
  const numericOnly = numeric.filter(v => v !== null && !isNaN(v))
  if (numericOnly.length === 0) {
    return { line: '', area: '', points: [] }
  }

  const max = Math.max(...numericOnly)
  const min = Math.min(...numericOnly)
  const range = Math.max(1, max - min)
  const step = numeric.length > 1 ? w / (numeric.length - 1) : w

  // Build points; mark nulls so we can create broken paths for gaps
  const points = numeric.map((v, i) => {
    const x = padding.left + (i * step)
    if (v === null || isNaN(v)) {
      return { x, y: padding.top + h, v: null, isNull: true }
    }
    const y = padding.top + h - ((v - min) / range) * h
    return { x, y, v: Number(v), isNull: false }
  })

  // Create line path with segments that skip nulls
  const segments = []
  let cur = []
  for (let i = 0; i < points.length; i++) {
    const p = points[i]
    if (p.isNull) {
      if (cur.length) { segments.push(cur); cur = [] }
    } else {
      cur.push(p)
    }
  }
  if (cur.length) segments.push(cur)

  const line = segments.map(seg => seg.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' ')).join(' ')

  // Area: draw area for each continuous segment
  const areaParts = segments.map(seg => {
    const segLine = seg.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' ')
    const lastX = seg[seg.length - 1].x.toFixed(2)
    const baseY = (padding.top + h).toFixed(2)
    const firstX = seg[0].x.toFixed(2)
    return `${segLine} L ${lastX} ${baseY} L ${firstX} ${baseY} Z`
  })
  const area = areaParts.join(' ')
  return { line, area, points }
}

// Auto-detect and normalize data order to match labels (labels expected oldest->newest)
function shouldReverseData(data, labels) {
  if (!Array.isArray(data) || !Array.isArray(labels) || data.length !== labels.length) return false
  // parse labels to dates
  const parsed = labels.map(l => { try { return new Date(l) } catch (e) { return null } })
  if (!parsed[0] || !parsed[parsed.length - 1]) return false
  // check labels ascending (oldest -> newest)
  if (parsed[0].getTime() >= parsed[parsed.length - 1].getTime()) return false

  const len = data.length
  const firstNonNull = data.findIndex(v => v !== null && v !== undefined)
  const lastNonNull = (() => { for (let i = data.length - 1; i >= 0; i--) if (data[i] !== null && data[i] !== undefined) return i; return -1 })()
  if (firstNonNull === -1 || lastNonNull === -1) return false

  // Heuristic: if non-null values are concentrated at the start (near index 0)
  // but labels end is the most recent date, then data probably came reversed.
  if (lastNonNull <= Math.floor(len * 0.2) && firstNonNull >= Math.floor(len * 0.0)) {
    return true
  }

  // Another heuristic: if first non-null is at the very end while labels ascend, data may be reversed
  if (firstNonNull >= Math.ceil(len * 0.8) && lastNonNull === len - 1 && parsed[parsed.length - 1].getTime() >= Date.now() - 48 * 3600 * 1000) {
    return true
  }

  return false
}

function normalizeOrder(data, labels) {
  if (!Array.isArray(data)) return data
  if (shouldReverseData(data, labels)) {
    try { console.info('[Sparkline] detected reversed series, auto-reversing data to match labels') } catch (e) {}
    return [...data].reverse()
  }
  return data
}

const normalizedData = computed(() => normalizeOrder(props.data || [], props.labels || []))

const built = computed(() => buildPaths(normalizedData.value))
const linePath = computed(() => built.value.line)
const areaPath = computed(() => built.value.area)
const points = computed(() => built.value.points || [])

function tooltipText(idx) {
  const p = points.value[idx]
  if (!p) return ''
  // show value only; parent can provide labels for dates
  return String(p.v)
}

function emitOpen() { emit('open') }

// ECharts related (lazy loaded)
const chartRoot = ref(null)
let chart = null
let echartsLib = null
let resizeObserver = null

const rootStyle = computed(() => {
  const w = typeof props.width === 'number' ? props.width + 'px' : String(props.width || 'auto')
  const h = typeof props.height === 'number' ? props.height + 'px' : String(props.height || 'auto')
  return { width: w, height: h }
})

const svgWidth = computed(() => (typeof props.width === 'number' ? props.width : '100%'))
const svgHeight = computed(() => (typeof props.height === 'number' ? props.height : 36))

async function initEcharts() {
  if (!chartRoot.value) return
  if (!echartsLib) {
    echartsLib = await import('echarts')
  }
  chart = echartsLib.init(chartRoot.value)
  // use normalized data to ensure labels align with values
  setEchartOption(normalizedData.value || [], props.labels || [])
  chart.on('click', params => emit('open', params))

  // Observe size changes so charts using % widths resize correctly
  try {
    if (window.ResizeObserver && chartRoot.value) {
      resizeObserver = new ResizeObserver(() => { if (chart) chart.resize() })
      resizeObserver.observe(chartRoot.value)
    }
  } catch (e) {
    // ignore
  }
}

function setEchartOption(data, labels) {
  if (!chart) return
  const safeLabels = Array.isArray(labels) && labels.length === data.length ? labels.map(d => { try { return new Date(d).toLocaleDateString() } catch (e) { return String(d) } }) : data.map((_, i) => i + '')
  
  const grid = props.showAxes 
    ? { left: 50, right: 20, top: 20, bottom: 30, containLabel: true } 
    : { left: 2, right: 2, top: 4, bottom: 4 }

  chart.setOption({
    tooltip: { trigger: 'axis', formatter: function (params) { if (!params || !params.length) return ''; const p = params[0]; const idx = p.dataIndex; const dateLabel = safeLabels[idx] || idx; return `${dateLabel}<br/>${p.seriesName}: ${p.data != null ? p.data.toLocaleString() : '-'}` } },
    grid: grid,
    xAxis: { type: 'category', show: props.showAxes, data: safeLabels },
    yAxis: { type: 'value', show: props.showAxes },
    series: [{ name: 'count', type: 'line', smooth: true, symbol: 'circle', symbolSize: 4, showSymbol: true, lineStyle: { width: 1.6, color: props.strokeColor }, areaStyle: { color: new echartsLib.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: props.strokeColor, opacity: 0.18 }, { offset: 1, color: props.strokeColor, opacity: 0 }]) }, data: data }],
    animation: false
  })
}

onMounted(async () => {
  if (props.useEcharts) {
    await initEcharts()
  }
})

watch(() => props.data, (val) => {
  if (props.useEcharts && chart) setEchartOption(normalizedData.value || [], props.labels || [])
})

watch(() => [props.width, props.height], async () => { if (chart) { await Promise.resolve(); chart.resize() } })

onBeforeUnmount(() => { if (chart) { chart.dispose(); chart = null } })

onBeforeUnmount(() => { if (resizeObserver && chartRoot.value) { try { resizeObserver.unobserve(chartRoot.value) } catch (e) {} resizeObserver = null } })
</script>

<style scoped>
.sparkline-root { display: block }
</style>
