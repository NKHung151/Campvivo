"use client";

import { useEffect, useRef, useState } from "react";
import { getToken } from "@/lib/authToken";
import { wsUrlFromApiUrl } from "@/lib/support";
import { API_BASE_URL } from "@/services/apiConfig";
import type { SupportMessage } from "@/types/api/support";

/** connecting: đang nối; online: đang nhận tin realtime; offline: mất kết nối hoặc bị từ chối (tin vẫn gửi/nhận được bằng REST). */
export type SocketState = "connecting" | "online" | "offline";

interface Options {
  /** false = không nối (chưa đăng nhập, sai vai trò...). */
  enabled: boolean;
  /** Có tin mới đẩy tới (tin của người khác; tin mình gửi đã có trong phản hồi REST nên server không đẩy lại). */
  onMessage: (message: SupportMessage) => void;
  /** Gọi mỗi khi kết nối thành công, kể cả nối lại: nơi để đồng bộ lại bằng REST vì có thể đã lỡ tin lúc mất kết nối (api.md 4.9). */
  onConnected?: () => void;
}

/**
 * Nhận tin hỗ trợ realtime qua STOMP/SockJS (docs/02-ky-thuat/api.md mục 4.9). Client chỉ NHẬN; gửi tin luôn bằng REST.
 * JWT đặt trong khung CONNECT (trình duyệt không đặt được header khi bắt tay WebSocket) và đọc lại token mới nhất mỗi lần nối lại.
 * Server từ chối CONNECT (token hết hạn / bị thu hồi) bằng khung ERROR: dừng thử lại để khỏi gõ cửa liên tục; lần gọi REST kế tiếp sẽ
 * nhận 401 và `apiClient` đưa người dùng về trang đăng nhập.
 */
export function useSupportSocket({ enabled, onMessage, onConnected }: Options): SocketState {
  const [state, setState] = useState<SocketState>("connecting");
  // Giữ hàm gọi lại mới nhất trong ref để đổi hàm không làm ngắt rồi nối lại kết nối.
  const onMessageRef = useRef(onMessage);
  const onConnectedRef = useRef(onConnected);
  onMessageRef.current = onMessage;
  onConnectedRef.current = onConnected;

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    let stop: (() => void) | null = null;
    setState("connecting");

    // Nạp thư viện trong effect để SSR không phải chạy sockjs-client (cần `window`).
    Promise.all([import("@stomp/stompjs"), import("sockjs-client")])
      .then(([stomp, sockjs]) => {
        if (cancelled) return;
        const SockJS = (sockjs.default ?? sockjs) as unknown as new (url: string) => WebSocket;
        const client = new stomp.Client({
          webSocketFactory: () => new SockJS(wsUrlFromApiUrl(API_BASE_URL)),
          reconnectDelay: 5000,
          beforeConnect: () => {
            client.connectHeaders = { Authorization: `Bearer ${getToken() ?? ""}` };
            if (!cancelled) setState((s) => (s === "online" ? s : "connecting"));
          },
          onConnect: () => {
            if (cancelled) return;
            client.subscribe("/user/queue/support", (frame) => {
              try {
                onMessageRef.current(JSON.parse(frame.body) as SupportMessage);
              } catch {
                /* khung hỏng: bỏ qua, lần đồng bộ REST kế tiếp sẽ bù */
              }
            });
            setState("online");
            onConnectedRef.current?.();
          },
          onWebSocketClose: () => {
            if (!cancelled) setState("offline");
          },
          onStompError: () => {
            if (cancelled) return;
            setState("offline");
            void client.deactivate();
          },
        });
        client.activate();
        stop = () => void client.deactivate();
      })
      .catch(() => {
        if (!cancelled) setState("offline");
      });

    return () => {
      cancelled = true;
      stop?.();
    };
  }, [enabled]);

  return enabled ? state : "offline";
}
