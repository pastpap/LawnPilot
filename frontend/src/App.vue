<script setup lang="ts">
import { ref } from "vue";
import { apiClient } from "./api/client";

const sampleInput = `5 5\n1 2 N\nLFLFLFLFF\n3 3 E\nFFRFFRFRRF`;
const inputText = ref(sampleInput);
const outputLines = ref<string[]>([]);
const error = ref("");
const loading = ref(false);

async function runSimulation() {
  loading.value = true;
  error.value = "";
  outputLines.value = [];

  const lines = inputText.value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  const { data, error: requestError } = await apiClient.POST(
    "/api/v1/simulations",
    {
      body: { inputLines: lines },
    },
  );

  loading.value = false;

  if (requestError) {
    error.value = "Simulation failed. Check backend logs and input format.";
    return;
  }

  outputLines.value = data?.outputLines ?? [];
}
</script>

<template>
  <main class="panel">
    <h1>LawnPilot Simulator</h1>
    <p>Run the existing mower simulation via REST using generated API types.</p>

    <textarea v-model="inputText" aria-label="Simulation input" />
    <button :disabled="loading" @click="runSimulation">
      {{ loading ? "Running..." : "Run simulation" }}
    </button>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="outputLines.length > 0" class="output">
      <div v-for="line in outputLines" :key="line">{{ line }}</div>
    </div>
  </main>
</template>
