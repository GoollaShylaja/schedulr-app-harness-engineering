import { NavLink } from "react-router-dom";
import { useAuth } from "../../lib/auth-context";

const LINKS = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/meetings", label: "Meetings" },
  { to: "/contacts", label: "Contacts" },
  { to: "/team", label: "Team" },
  { to: "/availability", label: "Availability" },
  { to: "/settings", label: "Settings" },
];

export function NavBar() {
  const { user, logout } = useAuth();

  return (
    <div className="app-nav">
      <h2>Schedulr</h2>
      <nav>
        {LINKS.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            className={({ isActive }) => (isActive ? "active" : "")}
          >
            {link.label}
          </NavLink>
        ))}
      </nav>
      <div className="nav-footer">
        <div>{user?.fullName}</div>
        <div>{user?.role}</div>
        <button className="btn btn-secondary" onClick={logout} style={{ marginTop: "0.75rem" }}>
          Log out
        </button>
      </div>
    </div>
  );
}
