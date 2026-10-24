"use client";

import { useMemo, useState } from "react";
import { Select, Space, Typography } from "antd";
import { SearchOutlined } from "@ant-design/icons";
import type { CategoryNode } from "@/types/api/catalog";

const LEVEL_PLACEHOLDER = ["Chọn danh mục", "Chọn danh mục con", "Chọn tiếp"];

const fold = (s: string) =>
  s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d");

/** Các danh mục từ gốc tới danh mục có id = `id` (rỗng nếu không tìm thấy). */
function pathTo(nodes: CategoryNode[], id: number | undefined, trail: CategoryNode[] = []): CategoryNode[] {
  if (id === undefined) return [];
  for (const n of nodes) {
    const here = [...trail, n];
    if (n.id === id) return here;
    const hit = pathTo(n.children, id, here);
    if (hit.length) return hit;
  }
  return [];
}

const SEARCH_LIMIT = 30;

interface FlatCategory {
  id: number;
  path: string;
  /** Tên đã bỏ dấu, chữ thường — để so khớp. */
  nameKey: string;
  pathKey: string;
  depth: number;
  isLeaf: boolean;
}

/** Duỗi cây thành danh sách có đường dẫn đầy đủ ("Đồ Cắm Trại › Lều - Phụ kiện lều"). */
function flatten(nodes: CategoryNode[], parents: string[] = [], out: FlatCategory[] = []): FlatCategory[] {
  for (const n of nodes) {
    const names = [...parents, n.name];
    const path = names.join(" › ");
    out.push({ id: n.id, path, nameKey: fold(n.name), pathKey: fold(path), depth: names.length, isLeaf: !n.children.length });
    flatten(n.children, names, out);
  }
  return out;
}

/**
 * Tìm danh mục ở mọi cấp: mỗi từ gõ vào (không dấu) phải có trong đường dẫn. Ưu tiên danh mục mà chính tên
 * của nó khớp, rồi danh mục cấp cuối, rồi đường dẫn ngắn.
 */
function searchCategories(all: FlatCategory[], query: string): FlatCategory[] {
  const words = fold(query).split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  const rank = (c: FlatCategory) => (words.every((w) => c.nameKey.includes(w)) ? 0 : 1);
  return all
    .filter((c) => words.every((w) => c.pathKey.includes(w)))
    .sort((a, b) => rank(a) - rank(b) || Number(b.isLeaf) - Number(a.isLeaf) || a.depth - b.depth || a.path.localeCompare(b.path, "vi"))
    .slice(0, SEARCH_LIMIT);
}

/**
 * Chọn danh mục lần lượt từng cấp (như Tỉnh → Huyện → Xã): chọn cấp trên thì ô bên dưới hiện các danh mục con.
 * Ô tìm nhanh phía trên: gõ tên (không dấu được) → chọn kết quả là các ô cấp tự điền theo đường dẫn.
 * Giá trị = id danh mục sâu nhất đã chọn. Dùng làm control trong Form.Item (value/onChange).
 */
export function CategoryCascadeSelect({
  tree,
  value,
  onChange,
  loading,
}: {
  tree: CategoryNode[];
  value?: number;
  onChange?: (id: number | undefined) => void;
  loading?: boolean;
}) {
  const [query, setQuery] = useState("");
  const all = useMemo(() => flatten(tree), [tree]);
  const results = useMemo(() => searchCategories(all, query), [all, query]);
  const path = pathTo(tree, value);
  // Mỗi cấp: danh sách lựa chọn = con của danh mục đã chọn ở cấp trên; thêm 1 ô trống nếu cấp cuối còn con
  const levels: { options: CategoryNode[]; selected?: CategoryNode }[] = [{ options: tree, selected: path[0] }];
  path.forEach((node, i) => {
    if (node.children.length) levels.push({ options: node.children, selected: path[i + 1] });
  });

  return (
    <Space direction="vertical" size={8} style={{ width: "100%" }}>
      <Select<number>
        value={null}
        showSearch
        searchValue={query}
        onSearch={setQuery}
        onSelect={(id) => {
          onChange?.(id);
          setQuery("");
        }}
        onBlur={() => setQuery("")}
        filterOption={false}
        suffixIcon={<SearchOutlined />}
        placeholder="Tìm nhanh danh mục (vd. den pin, tui ngu)"
        options={results.map((c) => ({ value: c.id, label: c.path }))}
        notFoundContent={query.trim() ? "Không có danh mục phù hợp" : "Gõ tên danh mục để tìm ở mọi cấp"}
        popupMatchSelectWidth={false}
        styles={{ popup: { root: { minWidth: 340, maxWidth: 520 } } }}
        listHeight={320}
      />
      {levels.map((level, i) => (
        <Select<number>
          key={i}
          value={level.selected?.id}
          // Xoá ô cấp i → giữ lại danh mục ở cấp trên (bỏ các cấp bên dưới)
          onChange={(id) => onChange?.(id ?? (i === 0 ? undefined : path[i - 1].id))}
          allowClear
          showSearch
          loading={loading && i === 0}
          placeholder={LEVEL_PLACEHOLDER[Math.min(i, LEVEL_PLACEHOLDER.length - 1)]}
          options={level.options.map((c) => ({ value: c.id, label: c.name, search: fold(c.name), childCount: c.children.length }))}
          optionRender={(o) => (
            <span>
              {o.label}
              {o.data.childCount > 0 && <Typography.Text type="secondary"> · {o.data.childCount} mục con</Typography.Text>}
            </span>
          )}
          filterOption={(input, opt) => (opt?.search ?? "").includes(fold(input.trim()))}
          notFoundContent="Không có danh mục phù hợp"
        />
      ))}
    </Space>
  );
}
