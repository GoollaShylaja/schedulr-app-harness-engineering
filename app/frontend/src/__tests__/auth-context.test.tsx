import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "../lib/auth-context";
import { clearToken, getToken } from "../lib/api-client";

function mockFetchOnce(status: number, body: unknown) {
  vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(),
    json: async () => body,
  } as unknown as Response);
}

function Consumer() {
  const { user, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="user">{user ? user.fullName : "anonymous"}</span>
      <button onClick={() => login("a@b.com", "password123")}>login</button>
      <button onClick={logout}>logout</button>
    </div>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    clearToken();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("persists the token and exposes the user after login", async () => {
    mockFetchOnce(200, {
      success: true,
      message: "OK",
      data: {
        token: "jwt-token",
        expiresAt: "2026-01-02T00:00:00Z",
        user: { id: "1", email: "a@b.com", fullName: "Ada Lovelace", timezone: "UTC", role: "member", teamId: "t1" },
      },
      timestamp: "",
    });

    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("anonymous"));

    await act(async () => {
      screen.getByText("login").click();
    });

    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("Ada Lovelace"));
    expect(getToken()).toBe("jwt-token");
  });

  it("clears the token and user on logout", async () => {
    mockFetchOnce(200, {
      success: true,
      message: "OK",
      data: {
        token: "jwt-token",
        expiresAt: "2026-01-02T00:00:00Z",
        user: { id: "1", email: "a@b.com", fullName: "Ada Lovelace", timezone: "UTC", role: "member", teamId: "t1" },
      },
      timestamp: "",
    });

    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    await act(async () => {
      screen.getByText("login").click();
    });
    await waitFor(() => expect(getToken()).toBe("jwt-token"));

    await act(async () => {
      screen.getByText("logout").click();
    });

    expect(getToken()).toBeNull();
    expect(screen.getByTestId("user")).toHaveTextContent("anonymous");
  });
});
