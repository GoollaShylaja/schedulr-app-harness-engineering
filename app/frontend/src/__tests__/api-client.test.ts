import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, clearToken, getToken, request, setToken } from "../lib/api-client";

function mockFetchOnce(status: number, body: unknown, headers: Record<string, string> = {}) {
  const response = {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(headers),
    json: async () => body,
    blob: async () => new Blob(),
  } as unknown as Response;
  vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(response);
}

describe("api-client", () => {
  beforeEach(() => {
    clearToken();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns data from a successful envelope", async () => {
    mockFetchOnce(200, {
      success: true,
      message: "OK",
      data: { id: "1" },
      timestamp: "2026-01-01T00:00:00Z",
    });

    const result = await request<{ id: string }>("/contacts/1");
    expect(result).toEqual({ id: "1" });
  });

  it("throws ApiError when the envelope reports failure", async () => {
    mockFetchOnce(400, {
      success: false,
      message: "Invalid request",
      data: null,
      timestamp: "2026-01-01T00:00:00Z",
    });

    await expect(request("/contacts")).rejects.toMatchObject({
      message: "Invalid request",
      status: 400,
    });
  });

  it("throws ApiError and does not throw for a 401 before clearing the token", async () => {
    setToken("abc123");
    mockFetchOnce(401, { success: false, message: "Could not validate credentials", data: null, timestamp: "" });

    await expect(request("/meetings")).rejects.toBeInstanceOf(ApiError);
    expect(getToken()).toBeNull();
  });

  it("attaches the Authorization bearer header when a token is present", async () => {
    setToken("my-token");
    mockFetchOnce(200, { success: true, message: "OK", data: [], timestamp: "" });

    await request("/meetings");

    const call = vi.mocked(fetch).mock.calls[0];
    const headers = call[1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer my-token");
  });
});
