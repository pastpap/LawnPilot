import { ApiError } from "./errors";

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

interface RequestOptions {
    method?: "GET" | "POST";
    headers?: Record<string, string>;
    body?: unknown;
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const response = await fetch(`${baseUrl}${path}`, {
        method: options.method ?? "GET",
        headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
            ...options.headers,
        },
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });

    const responseText = await response.text();

    if (!response.ok) {
        throw new ApiError(response.status, response.statusText, responseText);
    }

    if (!responseText) {
        return undefined as T;
    }

    return JSON.parse(responseText) as T;
}