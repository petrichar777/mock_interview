<script setup lang="ts">
import AppFooter from '@/components/layout/AppFooter.vue'
</script>

<template>
  <main class="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-[#f8f9fa] p-4">
    <div
      class="absolute left-0 top-0 h-64 w-64 -translate-x-1/2 -translate-y-1/2 rounded-full bg-rose-400 opacity-20 blur-3xl"
    />
    <div
      class="absolute bottom-0 right-0 h-80 w-80 translate-x-1/3 translate-y-1/3 rounded-full bg-orange-400 opacity-20 blur-3xl"
    />
    <div class="absolute right-1/4 top-1/2 h-48 w-48 rounded-full bg-emerald-400 opacity-10 blur-3xl" />
    <div class="flex flex-1 items-center justify-center w-full">
      <section
        class="relative z-10 w-full max-w-2xl rounded-3xl border border-gray-100 bg-white p-8 shadow-[0_10px_40px_-10px_rgba(0,0,0,0.08)] sm:p-10"
      >
        <RouterView />
      </section>
    </div>
    <AppFooter />
  </main>
</template>

<style>
.fade-in-up {
  animation: fadeInUp 0.6s ease-out forwards;
}

.delay-100 {
  animation-delay: 0.1s;
}

.delay-200 {
  animation-delay: 0.2s;
}

.delay-300 {
  animation-delay: 0.3s;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
