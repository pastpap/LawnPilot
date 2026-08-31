<script setup lang="ts">
import { computed, ref } from "vue";
import { toFriendlyErrorMessage, toApiError } from "../api/errors";
import { sendMowerCommand, getMowerCommandHistory } from "../api/tenantApi";
import type {
  MowerTelemetryDto,
  MowerCommandType,
  MowerCommandResultDto,
  TenantRole,
} from "../api/types";

interface Props {
  mower: MowerTelemetryDto | null;
  tenantId: string;
  fleetId: string;
  role: TenantRole;
}

const props = defineProps<Props>();
const emit = defineEmits<{ "command-sent": [commandId: string] }>();

const commandHistory = ref<MowerCommandResultDto[]>([]);
const loading = ref(false);
const error = ref("");
const successMessage = ref("");
const pendingCommand = ref<MowerCommandType | null>(null);
const showCommandHistory = ref(false);

const commandDescriptions: Record<MowerCommandType, string> = {
  PAUSE: "Pause mower at current location",
  RESUME: "Resume autonomous operation",
  RETURN_HOME: "Navigate back to charging dock",
  OVERRIDE: "Manual override mode (operator-directed)",
};

const commandDisabledReasons = computed(() => {
  if (!props.mower) return {};
  const status = props.mower.status;

  return {
    PAUSE: status === "paused" ? "Already paused" : null,
    RESUME: status !== "paused" ? "Must be paused to resume" : null,
    RETURN_HOME: status === "charging" ? "Already at dock" : null,
    OVERRIDE: status === "maintenance" ? "In maintenance mode" : null,
  };
});

const isOverrideWarning = (command: MowerCommandType): boolean => {
  return command === "OVERRIDE";
};

function canExecuteCommand(command: MowerCommandType): boolean {
  return commandDisabledReasons.value[command] === null;
}

async function loadCommandHistory(): Promise<void> {
  if (!props.mower) return;

  try {
    commandHistory.value = await getMowerCommandHistory({
      tenantId: props.tenantId,
      fleetId: props.fleetId,
      mowerId: props.mower.mowerId,
      role: props.role,
    });
  } catch (err) {
    console.warn("Failed to load command history:", err);
    commandHistory.value = [];
  }
}

async function executeCommand(commandType: MowerCommandType): Promise<void> {
  if (!props.mower || !canExecuteCommand(commandType)) return;

  loading.value = true;
  error.value = "";
  successMessage.value = "";
  pendingCommand.value = commandType;

  try {
    const result = await sendMowerCommand({
      tenantId: props.tenantId,
      fleetId: props.fleetId,
      mowerId: props.mower.mowerId,
      commandType,
      role: props.role,
    });

    successMessage.value = `Command ${commandType} accepted (ID: ${result.commandId})`;
    emit("command-sent", result.commandId);

    // Refresh history after command
    await loadCommandHistory();
  } catch (err) {
    error.value = toFriendlyErrorMessage(toApiError(err));
  } finally {
    loading.value = false;
    pendingCommand.value = null;
  }
}

function getStatusBadgeClass(status: string): string {
  switch (status) {
    case "cutting":
      return "badge-success";
    case "paused":
      return "badge-warning";
    case "charging":
      return "badge-info";
    case "transit":
      return "badge-secondary";
    case "maintenance":
      return "badge-danger";
    default:
      return "badge-secondary";
  }
}

function formatTimestamp(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString();
}

function getCommandStatusColor(status: string): string {
  switch (status) {
    case "COMPLETED":
      return "text-success";
    case "ACCEPTED":
    case "EXECUTING":
      return "text-info";
    case "FAILED":
    case "REJECTED":
      return "text-danger";
    case "PENDING":
      return "text-warning";
    default:
      return "text-secondary";
  }
}
</script>

<template>
  <div v-if="mower" class="mower-control-panel">
    <!-- Mower Header -->
    <div class="panel-header">
      <div class="mower-info">
        <h3>{{ mower.mowerId }}</h3>
        <div class="mower-meta">
          <span class="badge" :class="getStatusBadgeClass(mower.status)">
            {{ mower.status.toUpperCase() }}
          </span>
          <span class="battery-indicator">
            🔋 {{ mower.batteryPercent }}%
          </span>
        </div>
      </div>
      <div class="mower-stats">
        <div class="stat">
          <span class="label">Model:</span>
          <span class="value">{{ mower.model }}</span>
        </div>
        <div class="stat">
          <span class="label">Runtime:</span>
          <span class="value">{{ mower.runtimeMinutesToday }} min</span>
        </div>
        <div class="stat">
          <span class="label">Coverage:</span>
          <span class="value"
            >{{ mower.coverageTodayHa.toFixed(2) }}/{{ mower.targetCoverageHa.toFixed(2) }}
            ha</span
          >
        </div>
      </div>
    </div>

    <!-- Alerts -->
    <div v-if="error" class="alert alert-danger">
      {{ error }}
    </div>
    <div v-if="successMessage" class="alert alert-success">
      {{ successMessage }}
    </div>

    <!-- Command Buttons -->
    <div class="command-section">
      <div class="section-title">Remote Control</div>
      <div class="command-buttons">
        <button
          v-for="cmd in ['PAUSE', 'RESUME', 'RETURN_HOME', 'OVERRIDE'] as const"
          :key="cmd"
          class="command-btn"
          :class="{
            'btn-primary': !isOverrideWarning(cmd),
            'btn-warning': isOverrideWarning(cmd),
            'btn-disabled': !canExecuteCommand(cmd),
          }"
          :disabled="!canExecuteCommand(cmd) || loading"
          :title="commandDisabledReasons[cmd] || commandDescriptions[cmd]"
          @click="executeCommand(cmd)"
        >
          <span class="command-name">{{ cmd.replace("_", " ") }}</span>
          <span class="command-desc">{{ commandDescriptions[cmd] }}</span>
        </button>
      </div>
    </div>

    <!-- Command History Toggle -->
    <div class="history-section">
      <button class="history-toggle" @click="showCommandHistory = !showCommandHistory">
        {{ showCommandHistory ? "Hide" : "Show" }} Command History
      </button>

      <!-- Command History -->
      <div v-if="showCommandHistory && commandHistory.length > 0" class="command-history">
        <div class="history-title">Recent Commands</div>
        <div v-for="cmd in commandHistory.slice(0, 5)" :key="cmd.commandId" class="history-item">
          <div class="history-header">
            <span class="command-type">{{ cmd.commandType }}</span>
            <span class="history-time">{{ formatTimestamp(cmd.createdAt) }}</span>
          </div>
          <div :class="['history-status', getCommandStatusColor(cmd.status)]">
            {{ cmd.status }}
          </div>
          <div v-if="cmd.errorMessage" class="history-error">
            {{ cmd.errorMessage }}
          </div>
        </div>
      </div>
      <div v-else-if="showCommandHistory" class="no-history">No command history available</div>
    </div>

    <!-- Safety Notice -->
    <div class="safety-notice">
      <strong>⚠️ Safety Notice:</strong> Commands are applied with operator validation. OVERRIDE
      commands are audited and may require additional approval.
    </div>
  </div>
  <div v-else class="no-mower-selected">
    <p>Select a mower to view and control its status</p>
  </div>
</template>

<style scoped>
.mower-control-panel {
  background: var(--bg-secondary, #f5f5f5);
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding-bottom: 16px;
}

.mower-info h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
  color: var(--text-primary, #222);
}

.mower-meta {
  display: flex;
  gap: 12px;
  align-items: center;
}

.badge {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.badge-success {
  background: #23b884;
  color: white;
}

.badge-warning {
  background: #ffd166;
  color: #222;
}

.badge-info {
  background: #4ea3ff;
  color: white;
}

.badge-secondary {
  background: #b4c1d3;
  color: #222;
}

.badge-danger {
  background: #f58a3b;
  color: white;
}

.battery-indicator {
  font-size: 14px;
  color: var(--text-secondary, #666);
}

.mower-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  flex: 1;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat .label {
  font-size: 12px;
  color: var(--text-secondary, #666);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat .value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #222);
}

.alert {
  padding: 12px 16px;
  border-radius: 4px;
  font-size: 14px;
}

.alert-danger {
  background: #ffe0e0;
  color: #d32f2f;
  border: 1px solid #d32f2f;
}

.alert-success {
  background: #e8f5e9;
  color: #23b884;
  border: 1px solid #23b884;
}

.command-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-secondary, #666);
}

.command-buttons {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.command-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px 16px;
  border: none;
  border-radius: 6px;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-primary {
  background: #4ea3ff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #1e7dd6;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(78, 163, 255, 0.3);
}

.btn-warning {
  background: #ffd166;
  color: #222;
}

.btn-warning:hover:not(:disabled) {
  background: #ffb84d;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 209, 102, 0.3);
}

.btn-disabled,
.command-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: #ccc;
  color: #666;
}

.command-name {
  display: block;
  font-weight: 700;
}

.command-desc {
  display: block;
  font-size: 12px;
  font-weight: 400;
  opacity: 0.8;
}

.history-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-toggle {
  align-self: flex-start;
  background: transparent;
  border: 1px solid rgba(0, 0, 0, 0.2);
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.history-toggle:hover {
  background: rgba(0, 0, 0, 0.05);
}

.command-history {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 4px;
  padding: 12px;
}

.history-title {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-secondary, #666);
}

.history-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  background: white;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  font-size: 12px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.command-type {
  font-weight: 600;
  color: var(--text-primary, #222);
}

.history-time {
  color: var(--text-secondary, #666);
}

.history-status {
  font-weight: 500;
  padding: 2px 4px;
  border-radius: 2px;
}

.text-success {
  color: #23b884;
}

.text-info {
  color: #4ea3ff;
}

.text-danger {
  color: #f58a3b;
}

.text-warning {
  color: #ffd166;
}

.text-secondary {
  color: #b4c1d3;
}

.history-error {
  color: #f58a3b;
  font-size: 11px;
  margin-top: 2px;
}

.no-history {
  padding: 8px;
  color: var(--text-secondary, #666);
  font-size: 12px;
  text-align: center;
}

.safety-notice {
  background: #fffbea;
  border: 1px solid #ffc107;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  color: #856404;
}

.no-mower-selected {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-secondary, #666);
  font-size: 14px;
}
</style>
