import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import * as authApi from "./api/auth";
import { clearToken, getToken, registerUnauthorizedHandler, setToken } from "./api-client";
import type { UserResponse } from "./types";

interface AuthContextValue {
  user: UserResponse | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  useEffect(() => {
    registerUnauthorizedHandler(() => setUser(null));
  }, []);

  useEffect(() => {
    const token = getToken();
    if (!token) {
      setIsLoading(false);
      return;
    }
    authApi
      .getCurrentUser()
      .then(setUser)
      .catch(() => clearToken())
      .finally(() => setIsLoading(false));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response = await authApi.login({ email, password });
    setToken(response.token);
    setUser(response.user);
  }, []);

  const refreshUser = useCallback(async () => {
    const current = await authApi.getCurrentUser();
    setUser(current);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within an AuthProvider");
  return context;
}
