"use client";

import { useCallback } from "react";
import { ChatPanel } from "@/components/features/support/ChatPanel";
import { useConversationMessages } from "@/components/features/support/useConversationMessages";
import { useSupportSocket } from "@/components/features/support/useSupportSocket";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { listMyMessages, sendMyMessage } from "@/services/supportService";
import type { SupportMessage } from "@/types/api/support";

/**
 * Chat với nhân viên hỗ trợ (FR-26) của khách: một hội thoại duy nhất. Lịch sử và việc gửi tin đi qua REST; tin của nhân viên đến
 * ngay qua WebSocket. Mất kết nối thì vẫn gửi được; nối lại sẽ tải lại để không sót tin.
 */
export function SupportChat() {
  const { user } = useAuth();
  const isCustomer = user?.role === "CUSTOMER";
  const conv = useConversationMessages(isCustomer ? listMyMessages : null, user?.id);
  const { add, refresh } = conv;

  const connection = useSupportSocket({
    enabled: isCustomer,
    onMessage: add,
    onConnected: refresh,
  });

  const send = useCallback(
    async (content: string) => {
      const saved: SupportMessage = await sendMyMessage(content);
      add(saved);
    },
    [add],
  );

  if (!isCustomer) {
    return (
      <div className="cv-chat__notice">
        Chat hỗ trợ dành cho tài khoản khách hàng. Nhân viên trả lời khách tại trang <a href="/admin/support">Hỗ trợ khách hàng</a>.
      </div>
    );
  }

  return (
    <div style={{ height: 520 }}>
      <ChatPanel
        messages={conv.messages}
        viewerIsStaff={false}
        loading={conv.loading}
        error={conv.error}
        onRetry={conv.reload}
        hasMore={conv.hasMore}
        loadingOlder={conv.loadingOlder}
        onLoadOlder={conv.loadOlder}
        onSend={send}
        connection={connection}
        emptyText="Chưa có tin nhắn nào. Hãy nhắn cho Campvivo, nhân viên sẽ trả lời sớm nhất có thể."
      />
    </div>
  );
}
