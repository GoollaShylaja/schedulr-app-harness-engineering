import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "./components/layout/app-layout";
import { ProtectedRoute } from "./components/layout/protected-route";
import { AuthProvider } from "./lib/auth-context";
import { LoginPage } from "./pages/login/login-page";
import { DashboardPage } from "./pages/dashboard/dashboard-page";
import { MeetingsListPage } from "./pages/meetings/meetings-list-page";
import { MeetingDetailPage } from "./pages/meetings/meeting-detail-page";
import { ContactsListPage } from "./pages/contacts/contacts-list-page";
import { TeamPage } from "./pages/team/team-page";
import { AvailabilityPage } from "./pages/availability/availability-page";
import { SettingsPage } from "./pages/settings/settings-page";

export function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/meetings" element={<MeetingsListPage />} />
            <Route path="/meetings/:id" element={<MeetingDetailPage />} />
            <Route path="/contacts" element={<ContactsListPage />} />
            <Route path="/team" element={<TeamPage />} />
            <Route path="/availability" element={<AvailabilityPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  );
}
