"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { App, Button, Card, Col, Empty, Form, Input, Popconfirm, Row, Select, Space, Spin, Tag, Tooltip, Tree } from "antd";
import type { TreeDataNode } from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { SLUG_PATTERN, toSlug } from "@/lib/slug";
import { createCategory, deleteCategory, getAdminCategoryTree, updateCategory } from "@/services/admin/adminCategoryService";
import { PageHeader } from "@/components/admin/common/PageHeader";
import type { AdminCategoryNode } from "@/types/api/catalog";

interface FormValues {
  name: string;
  slug?: string;
  parentId?: number;
}

/** Đang sửa danh mục có id, hoặc đang tạo mới dưới `parentId` (undefined = gốc). */
type Mode = { kind: "edit"; id: number } | { kind: "create"; parentId?: number } | null;

const fold = (s: string) =>
  s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d");

function indexTree(nodes: AdminCategoryNode[], parents: AdminCategoryNode[] = [], out = new Map<number, { node: AdminCategoryNode; parents: AdminCategoryNode[] }>()) {
  for (const n of nodes) {
    out.set(n.id, { node: n, parents });
    indexTree(n.children, [...parents, n], out);
  }
  return out;
}

/** Tổng sản phẩm của danh mục và mọi danh mục con. */
const totalProducts = (n: AdminCategoryNode): number => n.productCount + n.children.reduce((s, c) => s + totalProducts(c), 0);

/**
 * Quản lý danh mục (FR-05, BR-13): cây bên trái, form bên phải. Tối đa 2 cấp (D-38): danh mục cha chỉ chọn được trong các
 * danh mục cấp 1 — đổi ngay trong form sửa. Danh mục đang có danh mục con hoặc gắn combo (D-23) phải ở cấp 1.
 */
export function CategoryManager() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [tree, setTree] = useState<AdminCategoryNode[] | null>(null);
  const [mode, setMode] = useState<Mode>(null);
  const [query, setQuery] = useState("");
  const [expanded, setExpanded] = useState<React.Key[]>([]);
  const [saving, setSaving] = useState(false);
  const index = useMemo(() => indexTree(tree ?? []), [tree]);
  const name = Form.useWatch("name", form) ?? "";

  const load = useCallback(async () => {
    try {
      const data = await getAdminCategoryTree();
      setTree(data);
      // Cây chỉ 2 cấp và ít nhóm → mở sẵn mọi danh mục cấp 1 lần đầu vào trang
      setExpanded((k) => (k.length ? k : data.map((n) => n.id)));
    } catch (e) {
      setTree([]);
      message.error(`Không tải được danh mục: ${getErrorMessage(e)}`);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const current = mode?.kind === "edit" ? index.get(mode.id)?.node : undefined;

  // Mở form theo chế độ
  useEffect(() => {
    if (!mode) return;
    if (mode.kind === "edit") {
      const n = index.get(mode.id)?.node;
      if (n) form.setFieldsValue({ name: n.name, slug: n.slug, parentId: n.parentId ?? undefined });
    } else {
      form.setFieldsValue({ name: "", slug: "", parentId: mode.parentId });
    }
    form.setFields([{ name: "name", errors: [] }, { name: "slug", errors: [] }]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  /** Cây cho antd Tree, lọc theo ô tìm (không dấu) — giữ nhánh cha của kết quả. */
  const treeData = useMemo(() => {
    const q = fold(query.trim());
    const build = (nodes: AdminCategoryNode[]): TreeDataNode[] =>
      nodes.flatMap((n) => {
        const children = build(n.children);
        if (q && !fold(n.name).includes(q) && !children.length) return [];
        const total = totalProducts(n);
        return [
          {
            key: n.id,
            title: (
              <span className="cv-cat-node">
                <span>{n.name}</span>
                {total > 0 && <span className="cv-cat-count">{total}</span>}
              </span>
            ),
            children,
          },
        ];
      });
    return build(tree ?? []);
  }, [tree, query]);

  const searchExpanded = useMemo(() => {
    if (!query.trim()) return null;
    const keys: number[] = [];
    const walk = (nodes: TreeDataNode[]) => nodes.forEach((n) => n.children?.length && (keys.push(n.key as number), walk(n.children)));
    walk(treeData);
    return keys;
  }, [treeData, query]);

  const save = async (v: FormValues) => {
    setSaving(true);
    const body = {
      name: v.name.trim(),
      slug: v.slug?.trim() || undefined,
      parentId: v.parentId ?? null,
    };
    try {
      const saved = mode?.kind === "edit" ? await updateCategory(mode.id, body) : await createCategory(body);
      message.success(mode?.kind === "edit" ? `Đã lưu "${saved.name}".` : `Đã tạo danh mục "${saved.name}".`);
      await load();
      if (saved.parentId) setExpanded((k) => Array.from(new Set([...k, saved.parentId!])));
      setMode({ kind: "edit", id: saved.id });
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
        form.setFields(Object.entries(e.fieldErrors).map(([f, msg]) => ({ name: f as keyof FormValues, errors: [msg] })));
      } else if (e instanceof ApiError && e.errorCode === "SLUG_ALREADY_EXISTS") {
        form.setFields([{ name: "slug", errors: [e.message] }]);
      } else if (e instanceof ApiError && e.errorCode === "CIRCULAR_CATEGORY_DEPENDENCY") {
        form.setFields([{ name: "parentId", errors: [e.message] }]);
      } else {
        message.error(getErrorMessage(e));
      }
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!current) return;
    try {
      await deleteCategory(current.id);
      message.success(`Đã xoá "${current.name}".`);
      setMode(null);
      await load();
    } catch (e) {
      message.error(getErrorMessage(e));
    }
  };

  const deleteBlocked = current
    ? current.children.length
      ? "Còn danh mục con — chuyển hoặc xoá danh mục con trước."
      : current.productCount || current.comboCount
        ? `Còn ${current.productCount} sản phẩm${current.comboCount ? `, ${current.comboCount} combo` : ""} — chuyển sang danh mục khác trước.`
        : null
    : null;

  /** Danh mục cha chọn được: chỉ danh mục cấp 1, trừ chính nó. */
  const parentOptions = (tree ?? [])
    .filter((n) => !(mode?.kind === "edit" && n.id === mode.id))
    .map((n) => ({ value: n.id, label: n.name }));
  /** Lý do không đổi được danh mục cha (danh mục cấp 1 đang có con hoặc combo). */
  const parentLocked = current
    ? current.children.length
      ? "Đang có danh mục con nên phải là danh mục cấp 1 (tối đa 2 cấp)."
      : current.comboCount && !current.parentId
        ? "Đang gắn combo — combo chỉ gắn danh mục cấp 1."
        : null
    : null;
  const isRoot = current ? !current.parentId : false;

  return (
    <>
    <PageHeader
      title="Danh mục"
      tags={tree ? <Tag bordered={false}>{index.size}</Tag> : null}
      actions={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setMode({ kind: "create" })}>
          Thêm danh mục gốc
        </Button>
      }
    />
    <Row gutter={20}>
      <Col xs={24} lg={10} xl={9}>
        <Card styles={{ body: { padding: 12 } }}>
          <Input.Search placeholder="Tìm danh mục (gõ không dấu được)" allowClear value={query} onChange={(e) => setQuery(e.target.value)} style={{ marginBottom: 8 }} />
          {tree === null ? (
            <div style={{ textAlign: "center", padding: 24 }}>
              <Spin />
            </div>
          ) : treeData.length ? (
            <div className="cv-cat-tree">
              <Tree
                showLine={{ showLeafIcon: false }}
                blockNode
                treeData={treeData}
                expandedKeys={searchExpanded ?? expanded}
                onExpand={(keys) => setExpanded(keys)}
                selectedKeys={mode?.kind === "edit" ? [mode.id] : []}
                onSelect={(keys) => keys.length && setMode({ kind: "edit", id: keys[0] as number })}
              />
            </div>
          ) : (
            <Empty description={query ? "Không có danh mục phù hợp" : "Chưa có danh mục"} />
          )}
        </Card>
      </Col>

      <Col xs={24} lg={14} xl={15}>
        {!mode ? (
          <Card>
            <Empty description="Chọn một danh mục để xem và sửa" />
          </Card>
        ) : (
          <Card
            title={
              mode.kind === "edit"
                ? current?.name
                : mode.parentId
                  ? `Thêm danh mục con của “${index.get(mode.parentId)?.node.name ?? ""}”`
                  : "Thêm danh mục gốc"
            }
            extra={
              mode.kind === "edit" && (
                <Space>
                  {isRoot && (
                    <Button icon={<PlusOutlined />} onClick={() => setMode({ kind: "create", parentId: mode.id })}>
                      Danh mục con
                    </Button>
                  )}
                  <Popconfirm
                    title={`Xoá danh mục “${current?.name}”?`}
                    okText="Xoá"
                    cancelText="Huỷ"
                    okButtonProps={{ danger: true }}
                    onConfirm={remove}
                    disabled={!!deleteBlocked}
                  >
                    <Tooltip title={deleteBlocked}>
                      <Button danger icon={<DeleteOutlined />} disabled={!!deleteBlocked}>
                        Xoá
                      </Button>
                    </Tooltip>
                  </Popconfirm>
                </Space>
              )
            }
          >
            <Form<FormValues> form={form} layout="vertical" requiredMark={false} onFinish={save} disabled={saving}>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="name"
                    label="Tên danh mục"
                    rules={[
                      { required: true, whitespace: true, message: "Vui lòng nhập tên danh mục." },
                      { max: 150, message: "Tối đa 150 ký tự." },
                    ]}
                  >
                    <Input placeholder="VD: Đèn cắm trại" maxLength={150} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="slug"
                    label="Đường dẫn (slug)"
                    rules={[
                      { max: 180, message: "Tối đa 180 ký tự." },
                      {
                        validator: (_, val?: string) =>
                          !val || SLUG_PATTERN.test(val)
                            ? Promise.resolve()
                            : Promise.reject(new Error("Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.")),
                      },
                    ]}
                  >
                    <Input addonBefore="/categories/" placeholder={current?.slug ?? (toSlug(name) || "tu-dong-tao")} />
                  </Form.Item>
                </Col>
              </Row>
              {/* Đổi danh mục cha chỉ khi sửa; thêm gốc thì không có cha, thêm con thì cha cố định theo nút đã bấm */}
              {mode.kind === "edit" ? (
                <Form.Item
                  name="parentId"
                  label="Danh mục cha"
                  extra={parentLocked ?? "Bỏ trống = danh mục cấp 1. Chỉ chọn được danh mục cấp 1 (tối đa 2 cấp)."}
                >
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder="Không có — đây là danh mục cấp 1"
                    options={parentOptions}
                    disabled={!!parentLocked}
                  />
                </Form.Item>
              ) : (
                mode.parentId && (
                  <>
                    <Form.Item label="Danh mục cha">
                      <Input value={index.get(mode.parentId)?.node.name ?? ""} disabled />
                    </Form.Item>
                    <Form.Item name="parentId" hidden>
                      <Input />
                    </Form.Item>
                  </>
                )
              )}

              <Space>
                <Button type="primary" htmlType="submit" loading={saving}>
                  {mode.kind === "edit" ? "Lưu thay đổi" : "Tạo danh mục"}
                </Button>
                <Button onClick={() => setMode(null)} disabled={false}>
                  Huỷ
                </Button>
              </Space>
            </Form>
          </Card>
        )}
      </Col>
    </Row>
    </>
  );
}
