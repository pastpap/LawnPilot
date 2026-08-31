export class ApiError extends Error {
    status: number;
    statusText: string;
    details: string;

    constructor(status: number, statusText: string, details = "") {
        super(details || `Request failed with status ${status}`);
        this.name = "ApiError";
        this.status = status;
        this.statusText = statusText;
        this.details = details;
    }
}

export function toApiError(error: unknown): ApiError {
    if (error instanceof ApiError) {
        return error;
    }

    if (error instanceof Error) {
        return new ApiError(0, "Network Error", error.message);
    }

    return new ApiError(0, "Network Error", "Unexpected network error.");
}

export function toFriendlyErrorMessage(error: ApiError): string {
    const details = error.details ? ` ${error.details}` : "";

    if (error.status === 0) {
        return `Could not reach the backend service.${details}`.trim();
    }

    if (error.status === 400) {
        return `Request is invalid. Check tenant, role, or payload.${details}`.trim();
    }

    if (error.status === 403) {
        return `Your selected role cannot perform this action.${details}`.trim();
    }

    if (error.status === 404) {
        return `Requested tenant or fleet was not found.${details}`.trim();
    }

    if (error.status === 409) {
        return `Resource already exists. Use a different identifier.${details}`.trim();
    }

    if (error.status >= 500) {
        return `Backend error (${error.status}). Please retry in a moment.${details}`.trim();
    }

    return `Request failed (${error.status} ${error.statusText}).${details}`.trim();
}