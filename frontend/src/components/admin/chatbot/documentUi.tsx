"use client";

import { Tag, Tooltip } from "antd";
import { CheckCircleOutlined, CloseCircleOutlined, SyncOutlined } from "@ant-design/icons";
import type { ChatbotDocument, ChatbotDocumentStatus } from "@/types/api/chatbot";

/** Khớp app.chatbot.knowledge.max-file-size (chatbot-service, mặc định 10MB) — kiểm sớm ở trình duyệt. */
export const MAX_FILE_BYTES = 10 * 1024 * 1024;
export const ACCEPT = ".pdf,.docx,.txt,.md";
/** Hỏi lại trạng thái khi còn tài liệu đang xử lý. */
export const POLL_MS = 3000;

export const STATUS_LABEL: Record<ChatbotDocumentStatus, string> = {
  PROCESSING: "Đang xử lý",
  READY: "Sẵn sàng",
  FAILED: "Lỗi",
};

export function StatusTag({ doc }: { doc: Pick<ChatbotDocument, "status" | "errorMessage"> }) {
  if (doc.status === "PROCESSING") {
    return (
      <Tag icon={<SyncOutlined spin />} color="processing">
        {STATUS_LABEL.PROCESSING}
      </Tag>
    );
  }
  if (doc.status === "READY") {
    return (
      <Tag icon={<CheckCircleOutlined />} color="success">
        {STATUS_LABEL.READY}
      </Tag>
    );
  }
  return (
    <Tooltip title={doc.errorMessage}>
      <Tag icon={<CloseCircleOutlined />} color="error">
        {STATUS_LABEL.FAILED}
      </Tag>
    </Tooltip>
  );
}

export function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function fileKind(fileName: string): string {
  const ext = fileName.split(".").pop()?.toLowerCase();
  return ext === "docx" ? "Word" : ext === "pdf" ? "PDF" : ext === "md" ? "Markdown" : ext === "txt" ? "Văn bản" : "Khác";
}

export const fmtDateTime = (iso: string) =>
  new Date(iso).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });

/** Kiểm file ở trình duyệt trước khi gửi; trả câu lỗi hoặc null. Server vẫn luôn kiểm lại. */
export function checkFile(file: File): string | null {
  const ext = "." + (file.name.split(".").pop()?.toLowerCase() ?? "");
  if (!ACCEPT.split(",").includes(ext)) return "Chỉ nhận tài liệu PDF, Word (.docx), .txt hoặc .md.";
  if (file.size > MAX_FILE_BYTES) return `Tài liệu vượt quá ${MAX_FILE_BYTES / (1024 * 1024)}MB.`;
  if (file.size === 0) return "Tài liệu rỗng.";
  return null;
}

/** Lưu blob thành file trên máy người dùng. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}
