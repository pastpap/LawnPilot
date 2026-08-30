# LawnPilot Growth and Modernization Roadmap

Date: 2026-08-30
Started by: stefan
Scope: Behavior-preserving modernization plus autonomous, plug-and-play, web-scale evolution

## Current State

LawnPilot is a correct but tightly coupled Java CLI simulator. It is a strong baseline for domain behavior but not yet ready for autonomous capabilities, plugin expansion, or web-scale usage.

Evidence:

- Hardcoded execution input path in main: lawnpilot/src/main/java/org/lawnpilot/Main.java
- Parser assumes happy-path shape and can fail on malformed input: lawnpilot/src/main/java/org/lawnpilot/InputParser.java
- Behavior logic and instruction decoding are coupled in mower execution: lawnpilot/src/main/java/org/lawnpilot/Mower.java
- Build configuration is minimal with no test harness: lawnpilot/build.gradle

## North Star Architecture

Adopt a hexagonal, modular architecture with the following layers:

1. Domain Core: immutable state model and deterministic command execution.
2. Application Layer: orchestration engine with pluggable strategies and policies.
3. Ports/Adapters: CLI adapter now, REST and event adapters for web later.
4. Plugin Runtime: independently loadable feature modules through stable contracts.

This keeps current behavior as the default strategy while allowing autonomous strategies to be added without rewriting core logic.

## Target Capabilities

1. Autonomous behavior mode in addition to instruction-driven mode.
2. Plug-and-play modules for decision strategy, collision policy, and output formatting.
3. Web API and Vue.js frontend with a path from MVP to multi-tenant operations.
4. Scale-ready runtime with observability, deployment automation, and reliability gates.
5. Tenant-scoped fleet onboarding, device registration, and role-based access control.
6. IoT sensor telemetry ingestion with replayable traces and fleet health monitoring.
7. Remote command and override control for individual mowers with safety guardrails.

## Multi-Phase Plan

## Phase 1 - Stabilization and Safety Net

Goal: Preserve current behavior and eliminate immediate engineering risk.

Work:

1. Add JUnit-based unit and integration baseline tests.
2. Add parser validation and explicit exception model.
3. Add file/stdin input path in main without changing default behavior semantics.

Quality gates:

1. Reference output remains exactly:
   1 3 N
   5 1 E
2. Malformed inputs produce readable errors.
3. Test suite passes before any structural refactors.
