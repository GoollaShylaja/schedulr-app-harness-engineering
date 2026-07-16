import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as meetingsApi from "../../lib/api/meetings";
import * as contactsApi from "../../lib/api/contacts";
import { useAuth } from "../../lib/auth-context";
import type { MeetingResponse } from "../../lib/types";

export function DashboardPage() {
  const { user } = useAuth();
  const [upcoming, setUpcoming] = useState<MeetingResponse[]>([]);
  const [contactCount, setContactCount] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      meetingsApi.listMeetings({ startAfter: new Date().toISOString(), size: 5 }),
      contactsApi.listContacts({ size: 1 }),
    ])
      .then(([meetingsPage, contactsPage]) => {
        setUpcoming(meetingsPage.content);
        setContactCount(contactsPage.totalElements);
      })
      .finally(() => setIsLoading(false));
  }, []);

  return (
    <div className="page">
      <h1>Welcome, {user?.fullName}</h1>

      {isLoading ? (
        <div className="page-loading">Loading…</div>
      ) : (
        <>
          <p>{contactCount} contacts in your team.</p>

          <h2>Upcoming meetings</h2>
          {upcoming.length === 0 ? (
            <p>No upcoming meetings.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Start</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {upcoming.map((meeting) => (
                  <tr key={meeting.id}>
                    <td>
                      <Link to={`/meetings/${meeting.id}`}>{meeting.title}</Link>
                    </td>
                    <td>{meeting.start}</td>
                    <td>
                      <span className="badge">{meeting.status}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
}
