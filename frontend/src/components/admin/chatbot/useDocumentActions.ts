"use client";

import { App } from "antd";
import { getErrorMessage } from "@/lib/errorMessages";
import {
  deleteDocument,
  downloadDocumentFile,
  reindexDocument,
  updateDocument,
} from "@/services/admin/adminChatbotService";
import type { ChatbotDocument } from "@/types/api/chatbot";
import { saveBlob } from "./documentUi";

/** Thao tác dùng chung giữa danh sách và trang chi tiết tài liệu. */
export function useDocumentActions({
  onChanged,
  onDeleted,
}: {
  onChanged: (doc: ChatbotDocument) => void;
  onDeleted: (id: number) => void;
}) {
  const { message, modal } = App.useApp();

  const toggle = async (doc: ChatbotDocument, isActive: boolean) => {
    try {
      const saved = await updateDocument(doc.id, { title: doc.title, description: doc.description, isActive });
      onChanged(saved);
      message.success(isActive ? `Chatbot sẽ dùng "${doc.title}".` : `Chatbot không dùng "${doc.title}" nữa.`);
    } catch (e) {
      message.error(getErrorMessage(e));
    }
  };

  const reindex = async (doc: ChatbotDocument) => {
    try {
      onChanged(await reindexDocument(doc.id));
      message.info(`Đang nạp lại "${doc.title}".`);
    } catch (e) {
      message.error(getErrorMessage(e));
    }
  };

  const download = async (doc: ChatbotDocument) => {
    try {
      const { blob, fileName } = await downloadDocumentFile(doc.id);
      saveBlob(blob, fileName ?? doc.fileName);
    } catch (e) {
      message.error(getErrorMessage(e));
    }
  };

  const remove = (doc: ChatbotDocument) => {
    modal.confirm({
      title: "Xoá tài liệu?",
      content: `"${doc.title}" và toàn bộ nội dung bot đã đọc từ tài liệu này sẽ bị xoá vĩnh viễn. Muốn tạm ngừng thì chọn "Tắt" thay vì xoá.`,
      okText: "Xoá",
      okButtonProps: { danger: true },
      cancelText: "Huỷ",
      onOk: async () => {
        try {
          await deleteDocument(doc.id);
          message.success(`Đã xoá "${doc.title}".`);
          onDeleted(doc.id);
        } catch (e) {
          message.error(getErrorMessage(e));
        }
      },
    });
  };

  return { toggle, reindex, download, remove };
}
