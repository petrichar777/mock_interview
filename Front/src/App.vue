<script setup lang="ts">
import AppPopup from '@/components/common/AppPopup.vue'
import AppToast from '@/components/common/AppToast.vue'
</script>

<template>
  <RouterView />
  <AppPopup />
  <AppToast />
</template>
