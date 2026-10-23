"use client";

import { createContext, useContext } from "react";
import { useAdminSession } from "@/hooks/useAdminSession";

type AdminSession = ReturnType<typeof useAdminSession>;

const AdminSessionContext = createContext<AdminSession | null>(null);

export function AdminSessionProvider({ children }: { children: React.ReactNode }) {
  const session = useAdminSession();
  return <AdminSessionContext.Provider value={session}>{children}</AdminSessionContext.Provider>;
}

/** Phiên admin hiện tại — chỉ dùng bên trong khu vực /admin. */
export function useAdmin(): AdminSession {
  const ctx = useContext(AdminSessionContext);
  if (!ctx) throw new Error("useAdmin() phải nằm trong <AdminSessionProvider>");
  return ctx;
}
