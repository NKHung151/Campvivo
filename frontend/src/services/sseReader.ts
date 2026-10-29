/** Một sự kiện Server-Sent Events: `event: <name>` + các dòng `data:` (nối bằng "\n"). */
export interface SseEvent {
  event: string;
  data: string;
}

/**
 * Đọc luồng `text/event-stream` từ `fetch` (EventSource không gửi được header Authorization nên không dùng được).
 * Gọi `onEvent` cho mỗi sự kiện hoàn chỉnh theo đúng thứ tự nhận.
 */
export async function readSse(body: ReadableStream<Uint8Array>, onEvent: (e: SseEvent) => void): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n?/g, "\n");
    let sep = buffer.indexOf("\n\n");
    while (sep >= 0) {
      dispatch(buffer.slice(0, sep), onEvent);
      buffer = buffer.slice(sep + 2);
      sep = buffer.indexOf("\n\n");
    }
  }
  buffer += decoder.decode();
  if (buffer.trim()) dispatch(buffer, onEvent);
}

function dispatch(block: string, onEvent: (e: SseEvent) => void): void {
  let event = "message";
  const data: string[] = [];
  for (const line of block.split("\n")) {
    if (line.startsWith(":")) continue;
    const idx = line.indexOf(":");
    const field = idx < 0 ? line : line.slice(0, idx);
    let value = idx < 0 ? "" : line.slice(idx + 1);
    if (value.startsWith(" ")) value = value.slice(1);
    if (field === "event") event = value;
    else if (field === "data") data.push(value);
  }
  if (data.length > 0) onEvent({ event, data: data.join("\n") });
}
