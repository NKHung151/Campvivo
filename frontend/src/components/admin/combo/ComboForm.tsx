"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, App, Button, Card, Col, Collapse, Empty, Form, Image, Input, InputNumber, Row, Select, Space, Switch, Tag, Tooltip, Typography } from "antd";
import { CloseOutlined, MinusOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { vnd } from "@/lib/format";
import { SLUG_PATTERN, foldText, toSlug } from "@/lib/slug";
import { getCategoryTree, listProducts } from "@/services/admin/adminProductService";
import { ImageListField } from "@/components/admin/common/ImageListField";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { RichTextEditor } from "@/components/admin/common/RichTextEditor";
import type { AdminComboDetail, AdminProductImage, AdminProductSummary, CategoryNode, ComboRequest } from "@/types/api/catalog";

/** Một dòng sản phẩm trong bộ combo (kèm thông tin hiển thị). */
interface ItemRow {
  productId: number;
  name: string;
  slug: string;
  imageUrl: string | null;
  price: number;
  active: boolean;
  /** Tồn kho khả dụng của sản phẩm (đóng gói trừ kho này — D-22). */
  available: number;
  quantity: number;
}

interface FormValues {
  name: string;
  slug?: string;
  description?: string;
  categoryId?: number;
  comboPrice?: number;
  isActive: boolean;
  items: ItemRow[];
  images: AdminProductImage[];
}

const MAX_ITEMS = 20;
const DISCOUNTS = [10, 15, 20];
const vndFormatter = (v: number | string | undefined) => (v === undefined || v === "" ? "" : `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, "."));
const vndParser = (v: string | undefined) => Number((v ?? "").replace(/\D/g, "")) as unknown as 0;

/**
 * Form thêm/sửa combo (FR-15, D-03, D-22). Combo là bộ đóng gói sẵn: mỗi bộ gồm các sản phẩm (kèm số lượng)
 * bên dưới; giá và tồn kho riêng. Tồn kho combo đổi bằng "Đóng gói / Tháo" ở khung Kho combo (`stockPanel`).
 * `packedSets` > 0 thì khoá phần sản phẩm (phải tháo hết trước khi đổi).
 */
export function ComboForm({
  initial,
  title,
  tags,
  submitText,
  onSubmit,
  stockPanel,
  packedSets = 0,
}: {
  initial?: AdminComboDetail;
  title: React.ReactNode;
  tags?: React.ReactNode;
  submitText: string;
  onSubmit: (body: ComboRequest) => Promise<void>;
  stockPanel?: React.ReactNode;
  packedSets?: number;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [tree, setTree] = useState<CategoryNode[] | null>(null);
  const name = Form.useWatch("name", form) ?? "";
  const items: ItemRow[] = Form.useWatch("items", form) ?? [];
  const comboPrice: number | undefined = Form.useWatch("comboPrice", form);

  useEffect(() => {
    getCategoryTree()
      .then(setTree)
      .catch((e) => {
        setTree([]);
        message.error(`Không tải được danh mục: ${getErrorMessage(e)}`);
      });
  }, [message]);

  const initialValues = useMemo<FormValues>(
    () =>
      initial
        ? {
            name: initial.name,
            slug: initial.slug,
            description: initial.description ?? undefined,
            categoryId: initial.categoryId,
            comboPrice: initial.comboPrice,
            isActive: initial.isActive,
            items: initial.items.map((i) => ({
              productId: i.productId,
              name: i.productName,
              slug: i.productSlug,
              imageUrl: i.productImageUrl,
              price: i.productPrice,
              active: i.productActive,
              available: i.productAvailableQuantity,
              quantity: i.quantity,
            })),
            images: initial.images,
          }
        : { name: "", isActive: true, items: [], images: [] },
    [initial],
  );

  const ownImages: AdminProductImage[] = Form.useWatch("images", form) ?? [];
  // Ảnh sản phẩm trong combo (hiển thị sau ảnh tự tải lên); chưa có ảnh tự tải lên thì ảnh đầu là ảnh đại diện
  const productImages = Array.from(new Set(items.map((i) => i.imageUrl).filter((u): u is string => Boolean(u))));
  const itemsTotal = items.reduce((s, i) => s + i.price * i.quantity, 0);
  const saving = itemsTotal && comboPrice ? itemsTotal - comboPrice : 0;

  const submit = async (v: FormValues) => {
    setSubmitting(true);
    try {
      await onSubmit({
        name: v.name.trim(),
        slug: v.slug?.trim() || undefined,
        description: v.description?.trim() || null,
        categoryId: v.categoryId!,
        comboPrice: v.comboPrice!,
        isActive: v.isActive,
        images: v.images ?? [],
        items: v.items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
      });
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
        form.setFields(Object.entries(e.fieldErrors).map(([f, msg]) => ({ name: f as keyof FormValues, errors: [msg] })));
        message.error(Object.values(e.fieldErrors)[0] ?? "Vui lòng kiểm tra lại các ô được đánh dấu.");
      } else if (e instanceof ApiError && e.errorCode === "SLUG_ALREADY_EXISTS") {
        form.setFields([{ name: "slug", errors: [e.message] }]);
        message.error(e.message);
      } else {
        message.error(getErrorMessage(e));
      }
    } finally {
      setSubmitting(false);
    }
  };


  return (
    <Form<FormValues>
      form={form}
      layout="vertical"
      initialValues={initialValues}
      onFinish={submit}
      onFinishFailed={() => message.error("Vui lòng kiểm tra lại các ô được đánh dấu.")}
      requiredMark={false}
      disabled={submitting}
      scrollToFirstError={{ behavior: "smooth", block: "center" }}
    >
      <PageHeader
        sticky
        breadcrumb={[{ label: "Combo", href: "/admin/combos" }, { label: initial ? "Sửa combo" : "Thêm combo" }]}
        title={title}
        tags={tags}
        actions={
          <>
            <Link href="/admin/combos">
              <Button disabled={false}>Huỷ</Button>
            </Link>
            <Button type="primary" htmlType="submit" loading={submitting}>
              {submitText}
            </Button>
          </>
        }
      />
      <Row gutter={20}>
        <Col xs={24} xl={16}>
          <Card
            title="Sản phẩm trong combo"
            extra={items.length > 0 && <Typography.Text type="secondary">{items.length} sản phẩm / bộ</Typography.Text>}
            style={{ marginBottom: 20 }}
          >
            {packedSets > 0 && (
              <Alert type="info" showIcon style={{ marginBottom: 12 }} message={`Đang có ${packedSets} bộ đã đóng gói — tháo hết để đổi sản phẩm.`} />
            )}
            <Form.Item
              name="items"
              noStyle
              rules={[
                {
                  validator: async (_, rows: ItemRow[] = []) => {
                    if (rows.length < 2) throw new Error("Combo cần ít nhất 2 sản phẩm.");
                    if (rows.length > MAX_ITEMS) throw new Error(`Tối đa ${MAX_ITEMS} sản phẩm.`);
                  },
                },
              ]}
            >
              <ComboItemsField locked={packedSets > 0} />
            </Form.Item>
            <Form.Item shouldUpdate noStyle>
              {() => {
                const errs = form.getFieldError("items");
                return errs.length ? <Typography.Text type="danger">{errs[0]}</Typography.Text> : null;
              }}
            </Form.Item>
          </Card>

          <Card title="Thông tin chung" style={{ marginBottom: 20 }}>
            <Form.Item
              name="name"
              label="Tên combo"
              rules={[
                { required: true, whitespace: true, message: "Vui lòng nhập tên combo." },
                { min: 2, max: 255, message: "Tên combo phải từ 2 đến 255 ký tự." },
              ]}
            >
              <Input placeholder="VD: Combo cắm trại 2 người cơ bản" maxLength={255} showCount />
            </Form.Item>
            <Form.Item name="description" label="Mô tả" style={{ marginBottom: 0 }} rules={[{ max: 50_000, message: "Mô tả quá dài." }]}>
              <RichTextEditor folder="combos" placeholder="Combo phù hợp cho chuyến đi nào, có gì đặc biệt…" />
            </Form.Item>
          </Card>

          {/* Ảnh combo = ảnh tự tải lên (trước) + ảnh chính của các sản phẩm trong combo (sau) — D-26 */}
          <Card title="Hình ảnh" style={{ marginBottom: 20 }}>
            <Form.Item
              name="images"
              noStyle
              rules={[
                {
                  validator: (_, list: AdminProductImage[] = []) =>
                    list.length <= 10 ? Promise.resolve() : Promise.reject(new Error("Tối đa 10 ảnh cho một combo.")),
                },
              ]}
            >
              <ImageListField folder="combos" />
            </Form.Item>
            {productImages.length > 0 && (
              <div className="cv-combo-gallery-block">
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Ảnh sản phẩm trong combo
                </Typography.Text>
                <div className="cv-combo-gallery">
                  {productImages.map((url, i) => (
                    <Image key={url} src={url} alt="" width={72} height={72} className={i === 0 && !ownImages.length ? "is-cover" : undefined} />
                  ))}
                </div>
              </div>
            )}
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card title="Trạng thái" style={{ marginBottom: 20 }}>
            <Form.Item
              name="isActive"
              style={{ marginBottom: 0 }}
              getValueProps={(v: boolean) => ({ value: v ? "on" : "off" })}
              normalize={(v: string) => v === "on"}
            >
              <Select
                options={[
                  { value: "on", label: <span className="cv-status is-on">Đang bán</span> },
                  { value: "off", label: <span className="cv-status is-off">Ẩn khỏi cửa hàng</span> },
                ]}
              />
            </Form.Item>
          </Card>

          <Card title="Giá combo" style={{ marginBottom: 20 }}>
            <div className="cv-price-row">
              <span>Tổng giá lẻ</span>
              <b>{itemsTotal ? vnd(itemsTotal, " ₫") : "—"}</b>
            </div>
            <Form.Item
              name="comboPrice"
              style={{ marginBottom: 8 }}
              rules={[
                { required: true, message: "Vui lòng nhập giá combo." },
                { type: "number", min: 1, message: "Giá combo phải lớn hơn 0." },
              ]}
            >
              <InputNumber<number>
                size="large"
                style={{ width: "100%" }}
                min={0}
                max={999_999_999_999}
                step={1000}
                precision={0}
                formatter={vndFormatter}
                parser={vndParser}
                addonAfter="₫"
                placeholder={itemsTotal ? vndFormatter(Math.round((itemsTotal * 0.9) / 1000) * 1000) : "Giá bán combo"}
              />
            </Form.Item>
            {itemsTotal > 0 && (
              <Space size={4} wrap style={{ marginBottom: 8 }}>
                {DISCOUNTS.map((d) => (
                  <Button key={d} size="small" onClick={() => form.setFieldValue("comboPrice", Math.round((itemsTotal * (100 - d)) / 100 / 1000) * 1000)}>
                    −{d}%
                  </Button>
                ))}
              </Space>
            )}
            {itemsTotal > 0 && comboPrice ? (
              saving > 0 ? (
                <div className="cv-saving">
                  Rẻ hơn mua lẻ <b>{vnd(saving, " ₫")}</b> ({Math.round((saving / itemsTotal) * 100)}%)
                </div>
              ) : (
                <div className="cv-saving is-bad">Không rẻ hơn mua lẻ</div>
              )
            ) : null}
          </Card>

          <Card title="Tổ chức" style={{ marginBottom: 20 }}>
            <Form.Item name="categoryId" label="Danh mục" style={{ marginBottom: 0 }} rules={[{ required: true, message: "Vui lòng chọn danh mục." }]}>
              <Select
                showSearch
                loading={tree === null}
                placeholder="Chọn danh mục"
                filterOption={(input, o) => foldText(String(o?.label ?? "")).includes(foldText(input.trim()))}
                options={(tree ?? []).map((c) => ({ value: c.id, label: c.name }))}
              />
            </Form.Item>
          </Card>

          {stockPanel}

          <Card title="Đường dẫn" style={{ marginBottom: 20 }}>
            <Form.Item
              name="slug"
              style={{ marginBottom: 0 }}
              rules={[
                { max: 280, message: "Slug tối đa 280 ký tự." },
                {
                  validator: (_, val?: string) =>
                    !val || SLUG_PATTERN.test(val)
                      ? Promise.resolve()
                      : Promise.reject(new Error("Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.")),
                },
              ]}
            >
              <Input addonBefore="/combos/" placeholder={initial ? initial.slug : toSlug(name) || "tu-dong-tao"} />
            </Form.Item>
          </Card>
        </Col>
      </Row>
    </Form>
  );
}

/** Danh sách sản phẩm của một bộ + ô tìm để thêm. value/onChange: ItemRow[]. */
function ComboItemsField({ value = [], onChange, locked }: { value?: ItemRow[]; onChange?: (rows: ItemRow[]) => void; locked: boolean }) {
  const { message } = App.useApp();
  const [options, setOptions] = useState<AdminProductSummary[]>([]);
  const [searching, setSearching] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const chosen = new Set(value.map((r) => r.productId));

  const search = (q: string) => {
    clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      setSearching(true);
      listProducts({ q: q.trim() || undefined, limit: 30, sort: "UPDATED_DESC" })
        .then((res) => setOptions(res.data))
        .catch(() => setOptions([]))
        .finally(() => setSearching(false));
    }, 300);
  };

  useEffect(() => {
    search("");
    return () => clearTimeout(timer.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const add = (id: number) => {
    const p = options.find((o) => o.id === id);
    if (!p) return;
    if (chosen.has(id)) {
      message.info("Sản phẩm đã có trong combo — tăng số lượng ở dòng đó.");
      return;
    }
    if (value.length >= MAX_ITEMS) {
      message.warning(`Tối đa ${MAX_ITEMS} sản phẩm.`);
      return;
    }
    onChange?.([
      ...value,
      {
        productId: p.id,
        name: p.name,
        slug: p.slug,
        imageUrl: p.primaryImageUrl,
        price: p.price,
        active: p.isActive,
        available: p.availableQuantity,
        quantity: 1,
      },
    ]);
  };

  const update = (id: number, patch: Partial<ItemRow>) => onChange?.(value.map((r) => (r.productId === id ? { ...r, ...patch } : r)));

  return (
    <div className="cv-combo-items">
      {value.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có sản phẩm — thêm ít nhất 2 sản phẩm" />
      ) : (
        value.map((r) => {
          return (
            <div key={r.productId} className="cv-combo-item">
              {r.imageUrl ? <Image src={r.imageUrl} alt={r.name} width={56} height={56} className="cv-prod-thumb" /> : <div className="cv-img-empty">—</div>}
              <div className="cv-combo-item-main">
                <Link href={`/admin/products/${r.productId}`} target="_blank" className="cv-prod-name">
                  {r.name}
                </Link>
                <div className="cv-combo-item-meta">
                  <Tag color={r.available > 0 ? "default" : "red"} bordered={false}>
                    Kho còn {r.available}
                  </Tag>
                  {!r.active && (
                    <Tooltip title="Sản phẩm đang ẩn trên cửa hàng — vẫn đóng gói vào combo được">
                      <Tag color="orange" bordered={false}>
                        Đang ẩn
                      </Tag>
                    </Tooltip>
                  )}
                </div>
              </div>
              <div className="cv-combo-item-qty">
                <Space.Compact size="small">
                  <Button icon={<MinusOutlined />} disabled={locked || r.quantity <= 1} onClick={() => update(r.productId, { quantity: r.quantity - 1 })} aria-label="Bớt" />
                  <InputNumber
                    size="small"
                    min={1}
                    max={99}
                    precision={0}
                    controls={false}
                    value={r.quantity}
                    disabled={locked}
                    onChange={(q) => update(r.productId, { quantity: q ?? 1 })}
                    style={{ width: 44, textAlign: "center" }}
                  />
                  <Button icon={<PlusOutlined />} disabled={locked || r.quantity >= 99} onClick={() => update(r.productId, { quantity: r.quantity + 1 })} aria-label="Thêm" />
                </Space.Compact>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  × {vnd(r.price, " ₫")}
                </Typography.Text>
              </div>
              <div className="cv-combo-item-total">{vnd(r.price * r.quantity, " ₫")}</div>
              <Button
                type="text"
                size="small"
                icon={<CloseOutlined />}
                disabled={locked}
                onClick={() => onChange?.(value.filter((x) => x.productId !== r.productId))}
                aria-label={`Bỏ ${r.name}`}
              />
            </div>
          );
        })
      )}
      {!locked && (
        <Select<number>
          showSearch
          value={null}
          suffixIcon={<SearchOutlined />}
          placeholder="＋ Thêm sản phẩm — gõ tên hoặc thương hiệu (không dấu được)"
          filterOption={false}
          onSearch={search}
          onSelect={add}
          loading={searching}
          notFoundContent={searching ? "Đang tìm…" : "Không có sản phẩm phù hợp"}
          style={{ width: "100%", marginTop: 12 }}
          options={options.map((p) => ({ value: p.id, label: p.name, data: p }))}
          optionRender={(o) => {
            const p = (o.data as { data: AdminProductSummary }).data;
            return (
              <div className="cv-combo-opt">
                {p.primaryImageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element -- ảnh Cloudinary nhỏ trong danh sách gợi ý
                  <img src={p.primaryImageUrl} alt="" width={36} height={36} />
                ) : (
                  <span className="cv-combo-opt-noimg" />
                )}
                <div className="cv-combo-opt-info">
                  <div className="cv-combo-opt-name">{p.name}</div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {p.brand ? `${p.brand} · ` : ""}
                    {vnd(p.price, " ₫")} · kho {p.availableQuantity}
                  </Typography.Text>
                </div>
                {chosen.has(p.id) && <Tag color="green">Đã thêm</Tag>}
              </div>
            );
          }}
        />
      )}
    </div>
  );
}
