<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

type SelectValue = string | number

interface SelectOption {
  label: string
  value: SelectValue
}

const props = withDefaults(
  defineProps<{
    modelValue: SelectValue | null
    options: SelectOption[]
    placeholder?: string
    variant?: 'rose' | 'orange'
    disabled?: boolean
  }>(),
  {
    placeholder: '请选择',
    variant: 'rose',
    disabled: false,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: SelectValue): void
  (e: 'change', value: SelectValue): void
}>()

const rootRef = ref<HTMLElement | null>(null)
const open = ref(false)

const selectedOption = computed(() => props.options.find((item) => item.value === props.modelValue))
const borderClass = computed(() => {
  if (props.variant === 'orange') {
    return open.value
      ? 'border-orange-400 ring-1 ring-orange-400'
      : 'border-white hover:border-orange-300 focus-visible:border-orange-400 focus-visible:ring-1 focus-visible:ring-orange-400'
  }
  return open.value
    ? 'border-rose-400 ring-1 ring-rose-400'
    : 'border-white hover:border-rose-300 focus-visible:border-rose-400 focus-visible:ring-1 focus-visible:ring-rose-400'
})
const optionHoverClass = computed(() => (props.variant === 'orange' ? 'hover:bg-orange-50 hover:text-orange-500' : 'hover:bg-rose-50 hover:text-rose-500'))

function close() {
  open.value = false
}

function toggle() {
  if (props.disabled) {
    return
  }
  open.value = !open.value
}

function selectOption(value: SelectValue) {
  emit('update:modelValue', value)
  emit('change', value)
  close()
}

function onDocumentPointerDown(event: Event) {
  if (!open.value) {
    return
  }
  const target = event.target as Node
  if (!rootRef.value?.contains(target)) {
    close()
  }
}

function onEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close()
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onEscape)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div ref="rootRef" class="relative" :class="open ? 'z-[120]' : 'z-10'">
    <button
      type="button"
      class="flex w-full items-center justify-between rounded-2xl bg-white/80 px-4 py-2.5 text-left text-sm font-bold text-gray-700 shadow-sm transition-all outline-none"
      :class="[borderClass, disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer']"
      :disabled="disabled"
      @click="toggle"
    >
      <span class="truncate">{{ selectedOption?.label || placeholder }}</span>
      <svg class="h-4 w-4 text-gray-400 transition-transform" :class="{ 'rotate-180': open }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M19 9l-7 7-7-7" />
      </svg>
    </button>

    <div
      v-if="open"
      class="absolute left-0 right-0 z-[130] mt-2 overflow-hidden rounded-2xl border border-white bg-white/95 shadow-[0_16px_40px_rgba(0,0,0,0.12)] backdrop-blur-xl"
    >
      <div class="max-h-72 overflow-y-auto p-1.5">
        <button
          v-for="item in options"
          :key="String(item.value)"
          type="button"
          class="flex w-full items-center justify-between rounded-xl px-3.5 py-2.5 text-left text-base font-bold transition-colors"
          :class="modelValue === item.value ? 'bg-zinc-700 text-white' : ['text-gray-700', optionHoverClass]"
          @click="selectOption(item.value)"
        >
          <span class="truncate">{{ item.label }}</span>
          <svg v-if="modelValue === item.value" class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>
