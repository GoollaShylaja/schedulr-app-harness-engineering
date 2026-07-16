import { Outlet } from "react-router-dom";
import { NavBar } from "./nav-bar";

export function AppLayout() {
  return (
    <div className="app-shell">
      <NavBar />
      <div className="app-content">
        <Outlet />
      </div>
    </div>
  );
}
