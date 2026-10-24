import type { TreeSelectProps } from "antd";
import type { CategoryNode } from "@/types/api/catalog";

export const PATH_SEPARATOR = " › ";

/**
 * Cây danh mục cho TreeSelect của antd. Mỗi nút có `fullPath` ("Đạp Xe › Xe đạp") để hiển thị sau khi
 * chọn (`treeNodeLabelProp="fullPath"`) và để tìm kiếm theo cả tên danh mục cha.
 */
export function toCategoryTreeData(nodes: CategoryNode[], parents: string[] = []): TreeSelectProps["treeData"] {
  return nodes.map((n) => {
    const path = [...parents, n.name];
    return {
      title: n.name,
      value: n.id,
      fullPath: path.join(PATH_SEPARATOR),
      children: toCategoryTreeData(n.children, path),
    };
  });
}

/** Props chung cho ô chọn danh mục: thu gọn, tìm được theo tên (không phân biệt hoa thường). */
export const categoryTreeSelectProps: Partial<TreeSelectProps> = {
  showSearch: true,
  treeNodeFilterProp: "fullPath",
  treeNodeLabelProp: "fullPath",
  treeLine: true,
  listHeight: 360,
  popupMatchSelectWidth: 340,
  filterTreeNode: (input, node) =>
    String((node as { fullPath?: string }).fullPath ?? "")
      .toLowerCase()
      .includes(input.trim().toLowerCase()),
};
