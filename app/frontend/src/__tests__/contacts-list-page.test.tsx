import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ContactsListPage } from "../pages/contacts/contacts-list-page";

function mockFetchOnce(status: number, body: unknown) {
  vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(),
    json: async () => body,
  } as unknown as Response);
}

describe("ContactsListPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders contacts and pagination controls", async () => {
    mockFetchOnce(200, {
      success: true,
      message: "OK",
      data: {
        content: [
          {
            id: "c1",
            name: "Grace Hopper",
            email: "grace@example.com",
            company: "Navy",
            phone: null,
            title: null,
            notes: null,
            stage: "customer",
            createdAt: "2026-01-01T00:00:00Z",
          },
        ],
        page: 0,
        size: 20,
        totalElements: 25,
        totalPages: 2,
      },
      timestamp: "",
    });

    render(<ContactsListPage />);

    await waitFor(() => expect(screen.getByText("Grace Hopper")).toBeInTheDocument());
    expect(screen.getByText("grace@example.com")).toBeInTheDocument();
    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
  });

  it("surfaces an API error", async () => {
    mockFetchOnce(500, { success: false, message: "An unexpected error occurred", data: null, timestamp: "" });

    render(<ContactsListPage />);

    await waitFor(() =>
      expect(screen.getByText("An unexpected error occurred")).toBeInTheDocument(),
    );
  });
});
