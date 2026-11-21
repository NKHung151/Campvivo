"use client";
/* eslint-disable @next/next/no-img-element -- catalogue imagery is served as-is */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import "@/styles/shop/search-suggest.css";
import { SearchIcon } from "@/components/ui/icons";
import { comboHref, productHref } from "@/lib/format";
import { fetchSuggestions } from "@/services/searchService";
import { MIN_SEARCH_LENGTH, type SearchSuggestionItem } from "@/types/api/search";

/** Chờ người dùng ngừng gõ chừng này mili giây rồi mới hỏi server, để không gọi API ở mỗi phím. */
const DEBOUNCE_MS = 250;

const hrefOf = (item: SearchSuggestionItem) => (item.type === "COMBO" ? comboHref(item.slug) : productHref(item.slug));

/**
 * Ô tìm kiếm ở header kèm gợi ý khi gõ (FR-07, `GET /search/suggestions`): từ 2 ký tự hiện tối đa vài sản phẩm / combo; mũi tên lên xuống để
 * chọn, Enter mở mục đang chọn (không chọn gì thì tìm theo từ khoá), Esc đóng, bấm ra ngoài cũng đóng. Mất mạng hay lỗi server chỉ làm danh
 * sách gợi ý biến mất: ô tìm kiếm vẫn gửi được như form thường.
 */
export function SearchBox({ initialQuery = "" }: { initialQuery?: string }) {
  const router = useRouter();
  const listId = useId();
  const rootRef = useRef<HTMLFormElement>(null);
  const [q, setQ] = useState(initialQuery);
  const [items, setItems] = useState<SearchSuggestionItem[]>([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1); // -1: chưa chọn; items.length: dòng "Xem tất cả"
  const [loading, setLoading] = useState(false);
  const [asked, setAsked] = useState("");   // từ khoá đã có phản hồi, để biết "không có gợi ý" khác "chưa hỏi"
  // Chỉ gợi ý sau khi NGƯỜI DÙNG gõ: trang kết quả mở sẵn với từ khoá trong ô, danh sách không được tự bật lên che nội dung.
  const [typed, setTyped] = useState(false);

  const key = q.trim();
  const canSuggest = key.length >= MIN_SEARCH_LENGTH;

  // Hỏi gợi ý sau khi ngừng gõ; huỷ yêu cầu cũ khi gõ tiếp hoặc rời đi.
  useEffect(() => {
    if (!canSuggest || !typed) {
      setItems([]);
      setOpen(false);
      setActive(-1);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      setLoading(true);
      fetchSuggestions(key, controller.signal)
        .then((list) => {
          if (controller.signal.aborted) return;
          setItems(list);
          setAsked(key);
          setActive(-1);
          setOpen(true);
        })
        .catch(() => {
          if (!controller.signal.aborted) {
            setItems([]);
            setOpen(false);
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [key, canSuggest, typed]);

  // Bấm ra ngoài thì đóng.
  useEffect(() => {
    const onDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, []);

  const total = items.length + 1; // thêm dòng "Xem tất cả kết quả"
  const searchHref = `/search?q=${encodeURIComponent(key)}`;
  const showList = open && canSuggest && asked === key;

  const go = (href: string) => {
    setOpen(false);
    setTyped(false); // đã chuyển trang: không tự mở lại danh sách cho tới khi gõ tiếp
    router.push(href);
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Escape") {
      setOpen(false);
      setActive(-1);
      return;
    }
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (!showList) {
        if (canSuggest && items.length) setOpen(true);
        return;
      }
      e.preventDefault();
      // Từ "chưa chọn" (-1): ↓ tới mục đầu, ↑ tới dòng cuối; đi hết một vòng thì quay lại đầu kia.
      setActive((a) => (e.key === "ArrowDown" ? (a + 1) % total : a <= 0 ? total - 1 : a - 1));
      return;
    }
    // Enter khi đang chọn một dòng: mở dòng đó thay vì gửi form. isComposing: đang gõ dấu tiếng Việt (Telex/VNI) thì Enter chỉ chốt chữ.
    if (e.key === "Enter" && showList && active >= 0 && !e.nativeEvent.isComposing) {
      e.preventDefault();
      go(active < items.length ? hrefOf(items[active]) : searchHref);
    }
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!key) return;
    go(searchHref);
  };

  return (
    <form id="searchBox" className="searchBox" onSubmit={submit} ref={rootRef} role="search">
      <input
        type="text"
        id="s"
        name="key"
        placeholder="Bạn chuẩn bị đi đâu?"
        autoComplete="off"
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          setTyped(true);
        }}
        onFocus={() => canSuggest && items.length && setOpen(true)}
        onKeyDown={onKeyDown}
      />
      <button type="submit" title="search">
        <SearchIcon />
      </button>
      {showList && (
        <ul className="cv-suggest" id={listId} role="listbox" aria-label="Gợi ý tìm kiếm">
          {items.length === 0 && !loading && <li className="cv-suggest__state">Không có gợi ý cho &quot;{key}&quot;.</li>}
          {items.map((it, i) => (
            <li key={`${it.type}-${it.slug}`} role="presentation">
              <Link
                id={`${listId}-${i}`}
                role="option"
                aria-selected={active === i}
                className={`cv-suggest__item${active === i ? " is-active" : ""}`}
                href={hrefOf(it)}
                prefetch={false}
                onMouseEnter={() => setActive(i)}
                onClick={() => {
                  setOpen(false);
                  setTyped(false);
                }}
              >
                <span className="cv-suggest__thumb">{it.imageUrl && <img loading="lazy" alt="" src={it.imageUrl} />}</span>
                <span className="cv-suggest__name">{it.name}</span>
                {it.type === "COMBO" && <span className="cv-suggest__kind">Combo</span>}
              </Link>
            </li>
          ))}
          <li role="presentation">
            <Link
              id={`${listId}-${items.length}`}
              role="option"
              aria-selected={active === items.length}
              className={`cv-suggest__item cv-suggest__all${active === items.length ? " is-active" : ""}`}
              href={searchHref}
              prefetch={false}
              onMouseEnter={() => setActive(items.length)}
              onClick={() => {
                setOpen(false);
                setTyped(false);
              }}
            >
              Xem tất cả kết quả cho &quot;{key}&quot;
            </Link>
          </li>
        </ul>
      )}
    </form>
  );
}
