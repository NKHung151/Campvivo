"use client";

import { useEffect, useRef, useState } from "react";
import { getToken } from "@/lib/authToken";
import { wsUrlFromApiUrl } from "@/lib/support";
import { API_BASE_URL } from "@/services/apiConfig";
import type { SocketState } from "@/components/features/support/useSupportSocket";
import type { NotificationPush } from "@/types/api/notification";

interface Options {
  /** false = không nối (chưa đăng nhập, sai khu vực...). */
  enabled: boolean;
  onPush: (push: NotificationPush) => void;
  /** Mỗi lần nối được, kể cả nối lại: đồng bộ lại bằng REST vì có thể đã lỡ khung lúc mất kết nối (api.md 4.9). */
  onConnected?: () => void;
}

/**
 * Nhận thông báo realtime qua STOMP/SockJS ở `/user/queue/notifications` (api.md mục 4.9). Cùng cách làm với
 * `useSupportSocket`: JWT trong khung CONNECT, đọc lại token mỗi lần nối, server từ chối CONNECT → dừng thử lại.
 * Kết nối riêng với chat (trang hỗ trợ có 2 kết nối — chấp nhận, xem plan notification.md mục "Rủi ro").
 */
export function useNotificationSocket({ enabled, onPush, onConnected }: Options): SocketState {
  const [state, setState] = useState<SocketState>("connecting");
  const onPushRef = useRef(onPush);
  const onConnectedRef = useRef(onConnected);
  onPushRef.current = onPush;
  onConnectedRef.current = onConnected;

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    let stop: (() => void) | null = null;
    setState("connecting");

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
            client.subscribe("/user/queue/notifications", (frame) => {
              try {
                onPushRef.current(JSON.parse(frame.body) as NotificationPush);
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
