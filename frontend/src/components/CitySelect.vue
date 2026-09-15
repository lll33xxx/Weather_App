<template>
  <div class="city-select" @click.stop v-bind="$attrs">
    <input
      ref="inputRef"
      v-model="keyword"
      type="text"
      :placeholder="placeholder"
      autocomplete="off"
      @focus="open"
      @input="open"
      @keydown="onKeydown"
    />
    <span v-if="keyword && clearable" class="city-clear-btn" @click="clear">×</span>
  </div>

  <Teleport to="body">
    <div v-if="visible" class="city-select-dropdown" :style="dropStyle">
      <div v-if="filtered.length === 0" class="city-select-empty">未找到匹配的城市</div>
      <div
        v-for="(city, i) in filtered"
        :key="city"
        class="city-select-item"
        :class="{ active: i === activeIndex }"
        @click="choose(city)"
        @mouseenter="activeIndex = i"
      >
        <span class="city-name" v-html="highlight(city)"></span>
        <span v-if="provinceOf(city)" class="city-province">{{ provinceOf(city) }}</span>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { cityProvince } from '@/utils/cityCoords'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { type: String, default: '' },
  cities: { type: Array, default: () => [] },
  placeholder: { type: String, default: '搜索或选择城市' },
  clearable: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'change'])

const keyword = ref(props.modelValue)
const visible = ref(false)
const activeIndex = ref(-1)
const inputRef = ref(null)
const dropStyle = ref({})

// 按中文拼音首字母排序
const collator = new Intl.Collator('zh-Hans-CN')

const filtered = computed(() => {
  const kw = keyword.value.toLowerCase().trim()
  let list = props.cities
  if (kw) list = list.filter((c) => c.toLowerCase().includes(kw))
  return [...list].sort((a, b) => collator.compare(a, b))
})

// 外部选中值变化时同步到输入框
watch(() => props.modelValue, (v) => {
  if (v !== keyword.value) keyword.value = v
})

// 下拉 fixed 定位：空间不足时向上展开
const updatePosition = () => {
  const el = inputRef.value
  if (!el) return
  const r = el.getBoundingClientRect()
  const dh = 284
  const up = window.innerHeight - r.bottom < dh + 8 && r.top > dh
  dropStyle.value = {
    left: r.left + 'px',
    width: r.width + 'px',
    ...(up
      ? { top: 'auto', bottom: (window.innerHeight - r.top + 4) + 'px' }
      : { top: (r.bottom + 4) + 'px', bottom: 'auto' })
  }
}

const open = () => {
  visible.value = true
  activeIndex.value = -1
  nextTick(updatePosition)
}

const provinceOf = (c) => cityProvince[c] || ''

// v-html 内容不受 scoped 样式作用，高亮用内联样式
const highlight = (text) => {
  const kw = keyword.value.trim()
  if (!kw) return text
  const idx = text.toLowerCase().indexOf(kw.toLowerCase())
  if (idx >= 0) {
    return (
      text.substring(0, idx) +
      '<span style="color:#f59e0b;font-weight:bold">' +
      text.substring(idx, idx + kw.length) +
      '</span>' +
      text.substring(idx + kw.length)
    )
  }
  return text
}

const choose = (city) => {
  keyword.value = city
  visible.value = false
  if (props.modelValue !== city) emit('update:modelValue', city)
  emit('change', city)
}

const clear = (e) => {
  e.stopPropagation()
  keyword.value = ''
  visible.value = true
  nextTick(updatePosition)
  if (props.modelValue !== '') emit('update:modelValue', '')
  emit('change', '')
  inputRef.value?.focus()
}

const onKeydown = (e) => {
  const items = filtered.value
  if (!items.length) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (!visible.value) { visible.value = true; nextTick(updatePosition); return }
    activeIndex.value = (activeIndex.value + 1) % items.length
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = activeIndex.value <= 0 ? items.length - 1 : activeIndex.value - 1
  } else if (e.key === 'Enter') {
    e.preventDefault()
    if (activeIndex.value >= 0 && items[activeIndex.value]) choose(items[activeIndex.value])
    else choose(items[0])
  } else if (e.key === 'Escape') {
    visible.value = false
  }
}

const onDocClick = (e) => {
  // 点在组件或下拉面板内不关闭
  if (e.target.closest('.city-select') || e.target.closest('.city-select-dropdown')) return
  visible.value = false
}
// 仅当页面/容器在下拉外部滚动时才关闭；下拉框自身内部滚动浏览城市时不关闭
const onDismiss = (e) => {
  const t = e && e.target
  if (t && t.closest && t.closest('.city-select-dropdown')) return
  visible.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  window.addEventListener('resize', onDismiss)
  window.addEventListener('scroll', onDismiss, true)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener('resize', onDismiss)
  window.removeEventListener('scroll', onDismiss, true)
})
</script>

<style scoped>
.city-select {
  position: relative;
  display: inline-block;
}

.city-select input {
  padding: 7px 32px 7px 14px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #1f2937;
  font-size: 14px;
  outline: none;
  width: 100%;
  min-width: 180px;
  cursor: text;
  box-sizing: border-box;
}

.city-select input:focus {
  border-color: #3b82f6;
}

.city-clear-btn {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  line-height: 16px;
  text-align: center;
  border-radius: 50%;
  background: #e5e7eb;
  color: #6b7280;
  font-size: 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.city-clear-btn:hover {
  background: #d1d5db;
  color: #1f2937;
}
</style>

<!-- 下拉面板 Teleport 到 body，需用全局样式（不受 scoped 限制） -->
<style>
.city-select-dropdown {
  position: fixed;
  max-height: 280px;
  overflow-y: auto;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  margin-top: 4px;
  z-index: 3000;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.12);
}

.city-select-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 14px;
  color: #374151;
  border-bottom: 1px solid #f3f4f6;
  transition: background 0.15s;
  white-space: nowrap;
}

.city-select-item:hover,
.city-select-item.active {
  background: #eff6ff;
  color: #3b82f6;
}

.city-name {
  flex: 0 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
}

.city-province {
  flex: 0 0 auto;
  font-size: 12px;
  color: #9ca3af;
}

.city-select-item:hover .city-province,
.city-select-item.active .city-province {
  color: #93c5fd;
}

.city-select-empty {
  padding: 12px 14px;
  color: #9ca3af;
  font-size: 13px;
  text-align: center;
}
</style>
