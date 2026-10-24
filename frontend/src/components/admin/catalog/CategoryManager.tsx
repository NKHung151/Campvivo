"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { App, Button, Card, Col, Empty, Form, Input, Modal, Popconfirm, Row, Space, Spin, Tag, Tooltip, Tree, Typography } from "antd";
import type { TreeDataNode } from "antd";
import { DeleteOutlined, DragOutlined, PlusOutlined } from "@ant-design/icons";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { SLUG_PATTERN, toSlug } from "@/lib/slug";
import { createCategory, deleteCategory, getAdminCategoryTree, updateCategory } from "@/services/admin/adminCategoryService";
import { CategoryCascadeSelect } from "@/components/admin/catalog/CategoryCascadeSelect";
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

/** Bỏ nhánh `id` khỏi cây (không cho chọn chính nó / danh mục con làm cha). */
const without = (nodes: AdminCategoryNode[], id: number): AdminCategoryNode[] =>
  nodes.filter((n) => n.id !== id).map((n) => ({ ...n, children: without(n.children, id) }));

/**
 * Quản lý danh mục đa cấp (FR-05, BR-13): cây bên trái, form bên phải.
 */
export function CategoryManager() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [tree, setTree] = useState<AdminCategoryNode[] | null>(null);
  const [mode, setMode] = useState<Mode>(null);
  const [query, setQuery] = useState("");
  const [expanded, setExpanded] = useState<React.Key[]>([]);
  const [saving, setSaving] = useState(false);
  // Hộp thoại "Chuyển vị trí": danh mục cha mới (undefined = đưa lên gốc)
  const [moveOpen, setMoveOpen] = useState(false);
  const [moveTarget, setMoveTarget] = useState<number | undefined>(undefined);
  const index = useMemo(() => indexTree(tree ?? []), [tree]);
  const name = Form.useWatch("name", form) ?? "";
  const parentId = Form.useWatch("parentId", form);

  const load = useCallback(async () => {
    try {
      setTree(await getAdminCategoryTree());
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

  const parentTree = mode?.kind === "edit" ? without(tree ?? [], mode.id) : (tree ?? []);
  /** Vị trí đang chọn trong form (có thể khác vị trí đã lưu nếu vừa "Chuyển vị trí"). */
  const locationNames = (id?: number) => {
    const hit = id ? index.get(id) : undefined;
    return hit ? [...hit.parents, hit.node].map((n) => n.name) : [];
  };
  const moved = mode?.kind === "edit" && (parentId ?? null) !== (current?.parentId ?? null);

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
                  <Button icon={<PlusOutlined />} onClick={() => setMode({ kind: "create", parentId: mode.id })}>
                    Danh mục con
                  </Button>
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
              <Form.Item name="parentId" hidden>
                <Input />
              </Form.Item>
              <Form.Item label="Vị trí" required>
                <Space wrap>
                  {parentId ? (
                    <span>
                      Nằm trong <b>{locationNames(parentId).join(" › ")}</b>
                    </span>
                  ) : (
                    <b>Danh mục gốc</b>
                  )}
                  {moved && <Tag color="orange">Chưa lưu</Tag>}
                  {mode.kind === "edit" && (
                    <Button
                      size="small"
                      icon={<DragOutlined />}
                      onClick={() => {
                        setMoveTarget(parentId);
                        setMoveOpen(true);
                      }}
                    >
                      Chuyển vị trí
                    </Button>
                  )}
                </Space>
              </Form.Item>
              <Modal
                open={moveOpen}
                title={`Chuyển “${current?.name ?? ""}” sang vị trí khác`}
                okText="Chọn vị trí này"
                cancelText="Huỷ"
                onCancel={() => setMoveOpen(false)}
                onOk={() => {
                  form.setFieldValue("parentId", moveTarget);
                  setMoveOpen(false);
                  message.info("Bấm “Lưu thay đổi” để áp dụng vị trí mới.");
                }}
                destroyOnHidden
              >
                <CategoryCascadeSelect tree={parentTree} value={moveTarget} onChange={setMoveTarget} />
                <Typography.Paragraph style={{ marginTop: 12, marginBottom: 0 }}>
                  Vị trí mới:{" "}
                  <b>{moveTarget ? `${locationNames(moveTarget).join(" › ")} › ${current?.name ?? ""}` : `${current?.name ?? ""} (danh mục gốc)`}</b>
                </Typography.Paragraph>
              </Modal>

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
