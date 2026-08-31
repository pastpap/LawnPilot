import { describe, expect, it } from "vitest";
import { ApiError, toApiError, toFriendlyErrorMessage } from "./errors";

describe("toFriendlyErrorMessage", () => {
  it("maps 403 to role authorization guidance", () => {
    const error = new ApiError(403, "Forbidden", "Role OPERATOR is not allowed.");
    expect(toFriendlyErrorMessage(error)).toContain("selected role cannot perform this action");
  });

  it("maps server errors to retry guidance", () => {
    const error = new ApiError(500, "Internal Server Error", "Something went wrong.");
    expect(toFriendlyErrorMessage(error)).toContain("Backend error (500)");
  });

  it("wraps unknown errors as network errors", () => {
    const normalized = toApiError(new Error("fetch failed"));
    expect(normalized.status).toBe(0);
    expect(toFriendlyErrorMessage(normalized)).toContain("Could not reach the backend service");
  });
});