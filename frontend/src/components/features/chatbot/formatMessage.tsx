import { Fragment, type ReactNode } from "react";

/**
 * Hiển thị câu trả lời của bot với định dạng tối thiểu: xuống dòng, danh sách (-, •, *, 1.), **đậm**.
 * Dựng thẳng React node (KHÔNG dangerouslySetInnerHTML) để chữ từ model không thể chèn HTML/script.
 */
export function formatMessage(text: string): ReactNode {
  const lines = text.replace(/\r\n?/g, "\n").split("\n");
  const blocks: ReactNode[] = [];
  let list: { ordered: boolean; items: string[] } | null = null;

  const flush = () => {
    if (!list) return;
    const items = list.items.map((it, i) => <li key={i}>{inline(it)}</li>);
    blocks.push(list.ordered ? <ol key={blocks.length}>{items}</ol> : <ul key={blocks.length}>{items}</ul>);
    list = null;
  };

  for (const raw of lines) {
    const line = raw.trimEnd();
    const bullet = /^\s*[-•*]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    if (bullet || numbered) {
      const ordered = Boolean(numbered);
      if (!list || list.ordered !== ordered) {
        flush();
        list = { ordered, items: [] };
      }
      list.items.push((bullet ?? numbered)![1]);
      continue;
    }
    flush();
    if (line.trim() === "") continue;
    const heading = /^#{1,6}\s+(.*)$/.exec(line);
    blocks.push(<p key={blocks.length}>{heading ? <strong>{inline(heading[1])}</strong> : inline(line)}</p>);
  }
  flush();
  return blocks;
}

/** **đậm** trong một dòng. */
function inline(text: string): ReactNode {
  const parts = text.split(/(\*\*[^*]+\*\*)/g).filter(Boolean);
  return parts.map((p, i) =>
    p.startsWith("**") && p.endsWith("**") && p.length > 4 ? <strong key={i}>{p.slice(2, -2)}</strong> : <Fragment key={i}>{p}</Fragment>,
  );
}
