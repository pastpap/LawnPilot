function clamp(min, max, value) {
  return Math.max(min, Math.min(max, value));
}

function round(value, digits = 6) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

function nextStatus(previous, battery) {
  if (battery <= 15) {
    return "charging";
  }
  const roll = Math.random();
  if (roll < 0.55) {
    return previous;
  }
  if (roll < 0.7) {
    return "cutting";
  }
  if (roll < 0.82) {
    return "transit";
  }
  if (roll < 0.92) {
    return "idle";
  }
  return "maintenance";
}

function drift(value, variance) {
  return value + (Math.random() * 2 - 1) * variance;
}

export function createMowerSimulator(seed) {
  const state = {
    mowerId: seed.mowerId,
    tenantId: seed.tenantId,
    fleetId: seed.fleetId,
    model: seed.model,
    areaId: seed.areaId,
    status: seed.status,
    batteryPercent: seed.batteryPercent,
    runtimeMinutesToday: seed.runtimeMinutesToday,
    coverageTodayHa: seed.coverageTodayHa,
    lat: seed.lat,
    lng: seed.lng,
    sequence: 0,
  };

  return {
    key: `${state.tenantId}/${state.fleetId}/${state.mowerId}`,
    step(intervalMs) {
      state.sequence += 1;
      state.status = nextStatus(state.status, state.batteryPercent);

      if (state.status === "cutting") {
        state.batteryPercent = clamp(
          0,
          100,
          state.batteryPercent - (0.3 + Math.random() * 1.1),
        );
        state.runtimeMinutesToday += intervalMs / 60000;
        state.coverageTodayHa = round(
          state.coverageTodayHa + (0.005 + Math.random() * 0.02),
          3,
        );
        state.lat = round(drift(state.lat, 0.00045));
        state.lng = round(drift(state.lng, 0.00045));
      } else if (state.status === "charging") {
        state.batteryPercent = clamp(
          0,
          100,
          state.batteryPercent + (0.9 + Math.random() * 2.4),
        );
        state.lat = round(drift(state.lat, 0.00012));
        state.lng = round(drift(state.lng, 0.00012));
      } else if (state.status === "transit") {
        state.batteryPercent = clamp(
          0,
          100,
          state.batteryPercent - (0.2 + Math.random() * 0.8),
        );
        state.runtimeMinutesToday += intervalMs / 120000;
        state.lat = round(drift(state.lat, 0.00085));
        state.lng = round(drift(state.lng, 0.00085));
      } else {
        state.batteryPercent = clamp(
          0,
          100,
          state.batteryPercent - Math.random() * 0.15,
        );
        state.lat = round(drift(state.lat, 0.00006));
        state.lng = round(drift(state.lng, 0.00006));
      }

      return {
        mowerId: state.mowerId,
        tenantId: state.tenantId,
        fleetId: state.fleetId,
        model: state.model,
        areaId: state.areaId,
        status: state.status,
        batteryPercent: Math.round(state.batteryPercent),
        runtimeMinutesToday: Math.round(state.runtimeMinutesToday),
        coverageTodayHa: round(state.coverageTodayHa, 3),
        latitude: state.lat,
        longitude: state.lng,
        sequence: state.sequence,
      };
    },
  };
}
