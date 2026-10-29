"use client";

import { Button, Result } from "antd";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { DocumentDetailView } from "@/components/admin/chatbot/DocumentDetailView";

/** /admin/chatbot/documents/[id] — chi tiết tài liệu cho chatbot (FR-28). */
export default function AdminChatbotDocumentPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  return (
    <RequireRole roles={ADMIN_ONLY}>
      {Number.isInteger(id) && id > 0 ? (
        <DocumentDetailView id={id} />
      ) : (
        <Result status="404" title="Không tìm thấy tài liệu" extra={<Button href="/admin/chatbot/documents">Về danh sách</Button>} />
      )}
    </RequireRole>
  );
}
