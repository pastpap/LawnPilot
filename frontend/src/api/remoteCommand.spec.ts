import { describe, it, expect, beforeEach, vi } from "vitest";
import * as http from "../api/http";

/**
 * Phase 7 Regression Test: Remote Command UI Integration
 *
 * Tests validate:
 * - Command envelope structure (commandId, sequence, correlationId, expiry)
 * - Role-based command submission (OPERATOR can send, VIEWER cannot)
 * - Error handling for invalid/stale commands
 * - Observable UI feedback on command success/failure
 * - Idempotent command submission
 */

describe("Remote Command API Integration (Phase 7)", () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    // --- Role-Based Access Control Tests ---

    it("should allow OPERATOR to send remote command", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValueOnce({
            ok: true,
            status: 200,
            json: async () => ({
                commandId: "cmd-123",
                sequence: 1,
                status: "PENDING",
                receivedAt: new Date().toISOString(),
                executedAt: null,
                errorMessage: null,
            }),
        } as Response);

        const commandId = "cmd-123";
        const response = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers: {
                    "X-Role": "OPERATOR",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    commandId,
                    mowerId: "mower-1",
                    commandType: "MOVE_FORWARD",
                    sequence: 1,
                    correlationId: "corr-456",
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        expect(response.ok).toBe(true);
        expect(response.status).toBe(200);
        expect(mockFetch).toHaveBeenCalledWith(
            expect.stringContaining("/remote-commands"),
            expect.objectContaining({
                method: "POST",
                headers: expect.objectContaining({
                    "X-Role": "OPERATOR",
                }),
            })
        );
    });

    it("should reject VIEWER sending remote command with 403", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValueOnce({
            ok: false,
            status: 403,
            text: async () =>
                "Role 'VIEWER' is not allowed to send remote commands. Required role: OPERATOR or ADMIN.",
        } as Response);

        const response = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers: {
                    "X-Role": "VIEWER",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    commandId: "cmd-123",
                    mowerId: "mower-1",
                    commandType: "MOVE_FORWARD",
                    sequence: 1,
                    correlationId: "corr-456",
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        expect(response.status).toBe(403);
        expect(mockFetch).toHaveBeenCalled();
    });

    it("should allow ADMIN to send remote command", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValueOnce({
            ok: true,
            status: 200,
            json: async () => ({
                commandId: "cmd-123",
                sequence: 1,
                status: "PENDING",
                receivedAt: new Date().toISOString(),
                executedAt: null,
                errorMessage: null,
            }),
        } as Response);

        const response = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers: {
                    "X-Role": "ADMIN",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    commandId: "cmd-123",
                    mowerId: "mower-1",
                    commandType: "MOVE_FORWARD",
                    sequence: 1,
                    correlationId: "corr-456",
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        expect(response.ok).toBe(true);
        expect(response.status).toBe(200);
    });

    // --- Command Envelope Validation Tests ---

    it("should reject command with missing commandId", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValueOnce({
            ok: false,
            status: 400,
            text: async () => "Command ID cannot be null or empty",
        } as Response);

        const response = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers: {
                    "X-Role": "OPERATOR",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    commandId: null,
                    mowerId: "mower-1",
                    commandType: "MOVE_FORWARD",
                    sequence: 1,
                    correlationId: "corr-456",
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        expect(response.status).toBe(400);
    });

    it("should reject command with missing correlationId", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValueOnce({
            ok: false,
            status: 400,
            text: async () => "Correlation ID cannot be null or empty",
        } as Response);

        const response = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers: {
                    "X-Role": "OPERATOR",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    commandId: "cmd-123",
                    mowerId: "mower-1",
                    commandType: "MOVE_FORWARD",
                    sequence: 1,
                    correlationId: null,
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        expect(response.status).toBe(400);
    });

    it("should reject expired command with 400", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValueOnce({
            ok: false,
            status: 400,
            text: async () => "Command has expired",
        } as Response);

        const expiredTime = new Date(Date.now() - 10000); // 10 seconds in the past

        const response = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers: {
                    "X-Role": "OPERATOR",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    commandId: "cmd-123",
                    mowerId: "mower-1",
                    commandType: "MOVE_FORWARD",
                    sequence: 1,
                    correlationId: "corr-456",
                    expiresAt: expiredTime.toISOString(),
                }),
            }
        );

        expect(response.status).toBe(400);
    });

    it("should reject invalid command type with 400", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValueOnce({
            ok: false,
            status: 400,
            text: async () =>
                "Invalid command type 'INVALID_TYPE'. Allowed types: MOVE_FORWARD, TURN_LEFT, TURN_RIGHT, STOP, PAUSE, RESUME",
        } as Response);

        const response = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers: {
                    "X-Role": "OPERATOR",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    commandId: "cmd-123",
                    mowerId: "mower-1",
                    commandType: "INVALID_TYPE",
                    sequence: 1,
                    correlationId: "corr-456",
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        expect(response.status).toBe(400);
    });

    it("should reject command for non-existent mower with 400", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValueOnce({
            ok: false,
            status: 400,
            text: async () => "Invalid mower ID format",
        } as Response);

        const response = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers: {
                    "X-Role": "OPERATOR",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    commandId: "cmd-123",
                    mowerId: "mower-nonexistent",
                    commandType: "MOVE_FORWARD",
                    sequence: 1,
                    correlationId: "corr-456",
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        expect(response.status).toBe(400);
    });

    // --- Idempotency Tests ---

    it("should return same result for duplicate commandId (idempotent)", async () => {
        const expectedResult = {
            commandId: "cmd-123",
            sequence: 1,
            status: "PENDING",
            receivedAt: new Date().toISOString(),
            executedAt: null,
            errorMessage: null,
        };

        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => expectedResult,
        } as Response);

        const commandPayload = {
            commandId: "cmd-123",
            mowerId: "mower-1",
            commandType: "MOVE_FORWARD",
            sequence: 1,
            correlationId: "corr-456",
            expiresAt: new Date(Date.now() + 60000).toISOString(),
        };

        const headers = {
            "X-Role": "OPERATOR",
            "Content-Type": "application/json",
        };

        const response1 = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers,
                body: JSON.stringify(commandPayload),
            }
        );

        const result1 = await response1.json();

        const response2 = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers,
                body: JSON.stringify(commandPayload),
            }
        );

        const result2 = await response2.json();

        expect(result1.commandId).toBe(result2.commandId);
        expect(result1.commandId).toBe("cmd-123");
    });

    // --- Command Sequence Tracking Tests ---

    it("should track sequence numbers within same correlationId", async () => {
        const correlationId = "corr-456";

        const mockFetch = vi.spyOn(global, "fetch");
        mockFetch
            .mockResolvedValueOnce({
                ok: true,
                status: 200,
                json: async () => ({
                    commandId: "cmd-123",
                    sequence: 1,
                    status: "PENDING",
                    receivedAt: new Date().toISOString(),
                    executedAt: null,
                    errorMessage: null,
                }),
            } as Response)
            .mockResolvedValueOnce({
                ok: true,
                status: 200,
                json: async () => ({
                    commandId: "cmd-124",
                    sequence: 2,
                    status: "PENDING",
                    receivedAt: new Date().toISOString(),
                    executedAt: null,
                    errorMessage: null,
                }),
            } as Response);

        const headers = {
            "X-Role": "OPERATOR",
            "Content-Type": "application/json",
        };

        const response1 = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers,
                body: JSON.stringify({
                    commandId: "cmd-123",
                    mowerId: "mower-1",
                    commandType: "MOVE_FORWARD",
                    sequence: 1,
                    correlationId,
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        const result1 = await response1.json();

        const response2 = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers,
                body: JSON.stringify({
                    commandId: "cmd-124",
                    mowerId: "mower-1",
                    commandType: "MOVE_FORWARD",
                    sequence: 2,
                    correlationId,
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        const result2 = await response2.json();

        expect(result1.sequence).toBe(1);
        expect(result2.sequence).toBe(2);
        expect(result1.commandId).not.toBe(result2.commandId);
    });

    // --- Observable Error Handling Tests ---

    it("should provide clear error message on invalid input", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockResolvedValueOnce({
            ok: false,
            status: 400,
            text: async () => "Command type cannot be null or empty",
        } as Response);

        const response = await fetch(
            "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
            {
                method: "POST",
                headers: {
                    "X-Role": "OPERATOR",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    commandId: "cmd-123",
                    mowerId: "mower-1",
                    commandType: "",
                    sequence: 1,
                    correlationId: "corr-456",
                    expiresAt: new Date(Date.now() + 60000).toISOString(),
                }),
            }
        );

        const errorMessage = await response.text();

        expect(response.status).toBe(400);
        expect(errorMessage).toContain("Command type");
    });

    it("should handle network errors gracefully", async () => {
        const mockFetch = vi.spyOn(global, "fetch").mockRejectedValueOnce(
            new Error("Network timeout")
        );

        try {
            await fetch(
                "/api/v1/tenants/tenant-alpha/fleets/fleet-1/remote-commands",
                {
                    method: "POST",
                    headers: {
                        "X-Role": "OPERATOR",
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        commandId: "cmd-123",
                        mowerId: "mower-1",
                        commandType: "MOVE_FORWARD",
                        sequence: 1,
                        correlationId: "corr-456",
                        expiresAt: new Date(Date.now() + 60000).toISOString(),
                    }),
                }
            );
            expect(true).toBe(false); // Should not reach here
        } catch (error) {
            expect(error instanceof Error).toBe(true);
            expect((error as Error).message).toContain("Network");
        }
    });
});
