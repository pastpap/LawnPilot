<script setup lang="ts">
import { computed, ref } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

const route = useRoute();
const isDark = ref(true);

const navItems = [
  { name: "Dashboard", path: "/dashboard" },
  { name: "Fleet", path: "/fleet" },
  { name: "Tracking", path: "/tracking" },
  { name: "Analytics", path: "/analytics" },
];

const currentSection = computed(() => {
  return navItems.find((item) => item.path === route.path)?.name ?? "Dashboard";
});
</script>

<template>
  <main class="dashboard-shell" :class="isDark ? 'theme-dark' : 'theme-light'">
    <header class="workspace-header">
      <div class="brand-mark" aria-label="LawnPilot brand">LP</div>

      <nav class="section-tabs" aria-label="Primary navigation">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="tab-link"
          active-class="active"
        >
          {{ item.name }}
        </RouterLink>
      </nav>

      <div class="header-actions">
        <button
          class="icon-button"
          type="button"
          :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
          :title="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
          @click="isDark = !isDark"
        >
          <span aria-hidden="true">{{ isDark ? "☀" : "☾" }}</span>
        </button>
        <div class="avatar" aria-label="User profile">A</div>
      </div>
    </header>

    <div class="section-title">
      <span class="eyebrow">Operations</span>
      <h1>{{ currentSection }}</h1>
    </div>

    <RouterView />
  </main>
</template>
