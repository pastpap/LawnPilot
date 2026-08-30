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

Completion update (2026-08-30): Completed and validated.

## Phase 2 - Domain and Modularity Foundation

Goal: Prepare clean extension seams while keeping behavior parity.

Work:

1. Introduce value objects for position and parsed mower instructions.
2. Extract direction turning/movement helpers to reduce duplication.
3. Split parser responsibilities into focused components.
4. Introduce command abstraction for L/R/F execution.

Quality gates:

1. No output changes for valid existing scenarios.
2. Domain logic is testable independent of I/O.
3. Cyclomatic complexity in main execution path decreases.

Completion update (2026-08-30): Completed and validated.

Verification notes:

- Executed test command: ./gradlew test
- Result: BUILD SUCCESSFUL
- Phase 2 implementation evidence: added Position and ParsedMowerInstructions value objects, extracted LawnDefinitionParser/MowerDefinitionParser/InstructionSequenceParser/ListLineTokenizer, introduced MowerCommand plus InstructionCommand abstraction, and moved turn/move-forward helpers into Direction and Mower collaboration.

## Phase 3 - Plugin Runtime Introduction

Goal: Support plug-and-play features without core rewrites.

Initial plugin contracts:

1. DecisionStrategy: instruction-driven default and autonomous alternatives.
2. CollisionPolicy: current boundary-only default and multi-mower policy options.
3. OutputFormatter: current text output default and JSON/web-friendly formats.

Work:

1. Defined plugin interfaces and default implementations.
2. Added plugin registry and versioned contract metadata checks.
3. Added lifecycle management hooks (register, enable, disable).
4. Added runtime fallback isolation so plugin failure does not crash simulation.

Quality gates:

1. Default plugins reproduce baseline behavior exactly.
2. Plugin failure is isolated and does not crash core engine.
3. Plugin contract compatibility tests are enforced.

Completion update (2026-08-30): Completed and validated.

Verification notes:

- Executed test command: ./gradlew test
- Result: BUILD SUCCESSFUL
- Behavior parity evidence: SimulationEngine default plugin test preserves reference outputs 1 3 N and 5 1 E.
- Isolation evidence: failing DecisionStrategy, CollisionPolicy, and OutputFormatter tests confirm runtime fallback to baseline behavior.
- Contract enforcement evidence: PluginRegistry tests reject incompatible contract versions and verify register/enable/disable lifecycle behavior.
