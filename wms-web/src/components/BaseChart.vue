<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { echarts } from '../utils/charts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '280px' },
})

const el = ref(null)
let chart = null

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  chart.setOption(props.option, true)
}

function resize() {
  if (chart) chart.resize()
}

onMounted(() => {
  render()
  if (typeof window !== 'undefined') window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('resize', resize)
  if (chart) { chart.dispose(); chart = null }
})

watch(() => props.option, render, { deep: true })
</script>

<template>
  <div ref="el" :style="{ width: '100%', height }" />
</template>
