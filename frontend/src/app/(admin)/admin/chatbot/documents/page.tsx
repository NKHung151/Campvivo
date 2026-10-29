"use client";

import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { DocumentListView } from "@/components/admin/chatbot/DocumentListView";

/** /admin/chatbot/documents — tài liệu cho chatbot (FR-28). */
export default function AdminChatbotDocumentsPage() {
  return (
    <RequireRole roles={ADMIN_ONLY}>
      <DocumentListView />
    </RequireRole>
  );
}
