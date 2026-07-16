import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MeetingsListPage } from "../pages/meetings/meetings-list-page";

function mockFetchOnce(status: number, body: unknown) {
  vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(),
    json: async () => body,
  } as unknown as Response);
}

describe("MeetingsListPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders meetings returned from the API", async () => {
    mockFetchOnce(200, {
      success: true,
      message: "OK",
      data: {
        content: [
          {
            id: "m1",
            title: "Kickoff call",
            host: "Ada Lovelace",
            hostId: "u1",
            start: "2026-07-20 16:00 CDT",
            end: "2026-07-20 16:30 CDT",
            timezone: "America/Chicago",
            status: "scheduled",
            notes: null,
            inviteeCount: 2,
            invitees: [],
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      timestamp: "",
    });

    render(
      <MemoryRouter>
        <MeetingsListPage />
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText("Kickoff call")).toBeInTheDocument());
    expect(screen.getByText("2026-07-20 16:00 CDT")).toBeInTheDocument();
  });

  it("shows an empty state with no meetings", async () => {
    mockFetchOnce(200, {
      success: true,
      message: "OK",
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
      timestamp: "",
    });

    render(
      <MemoryRouter>
        <MeetingsListPage />
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText("No meetings found.")).toBeInTheDocument());
  });
});
