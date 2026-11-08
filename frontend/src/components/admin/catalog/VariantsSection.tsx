"use client";

import { useState } from "react";
import { Button, Card, Form, Input, Switch, Table, Tag, Typography } from "antd";
import { CloseOutlined, DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import type { AdminProductDetail, ProductOptionRequest, ProductVariantRequest } from "@/types/api/catalog";

/** Một giá trị trong form: `key` cố định suốt phiên sửa (đổi tên vẫn giữ SKU / bật-tắt của biến thể). */
export interface OptionValueField {
  id?: number;
  key: string;
  value: string;
}

export interface OptionField {
  id?: number;
  name: string;
  values: OptionValueField[];
}

export type VariantSettings = Record<string, { sku?: string; isActive?: boolean }>;

export const MAX_OPTIONS = 2;
export const MAX_VARIANTS = 100;

let seq = 0;
const newKey = () => `n${Date.now().toString(36)}${(seq++).toString(36)}`;
const clean = (s?: string) => (s ?? "").trim();

/** Tuỳ chọn đã bỏ ô trống — dùng cho cả bảng biến thể và lúc gửi. */
function usableOptions(options: OptionField[] = []): OptionField[] {
  return options
    .map((o) => ({ ...o, values: (o?.values ?? []).filter((v) => clean(v?.value)) }))
    .filter((o) => clean(o?.name) && o.values.length > 0);
}

/** Mọi tổ hợp giá trị (tuỳ chọn 1 ngoài, tuỳ chọn 2 trong); không có tuỳ chọn → 1 tổ hợp rỗng (biến thể mặc định). */
function combinations(options: OptionField[]): OptionValueField[][] {
  return options.reduce<OptionValueField[][]>((acc, o) => acc.flatMap((prefix) => o.values.map((v) => [...prefix, v])), [[]]);
}

const comboKey = (combo: OptionValueField[]) => combo.map((v) => v.key).join("|");

/** Giá trị ban đầu của form từ chi tiết sản phẩm. */
export function initialVariantFields(initial?: AdminProductDetail): { options: OptionField[]; variantSettings: VariantSettings } {
  if (!initial) return { options: [], variantSettings: {} };
  const options: OptionField[] = initial.options.map((o) => ({
    id: o.id,
    name: o.name,
    values: o.values.map((v) => ({ id: v.id, key: `v${v.id}`, value: v.value })),
  }));
  const variantSettings: VariantSettings = {};
  initial.variants.forEach((variant) => {
    variantSettings[variantKeyOf(options, variant.values)] = { sku: variant.sku ?? undefined, isActive: variant.isActive };
  });
  return { options, variantSettings };
}

function variantKeyOf(options: OptionField[], values: string[]): string {
  return values
    .map((value, k) => options[k]?.values.find((v) => v.value.toLowerCase() === value.toLowerCase())?.key ?? "?")
    .join("|");
}

/** Phần `options` + `variants` của ProductRequest. */
export function toVariantRequest(options: OptionField[] = [], settings: VariantSettings = {}): {
  options: ProductOptionRequest[];
  variants: ProductVariantRequest[];
} {
  const usable = usableOptions(options);
  return {
    options: usable.map((o) => ({
      id: o.id ?? null,
      name: clean(o.name),
      values: o.values.map((v) => ({ id: v.id ?? null, value: clean(v.value) })),
    })),
    variants: combinations(usable).map((combo) => {
      const s = settings[comboKey(combo)];
      return { values: combo.map((v) => clean(v.value)), sku: clean(s?.sku) || null, isActive: s?.isActive ?? true };
    }),
  };
}

/**
 * Tuỳ chọn + biến thể (D-37): tối đa 2 tuỳ chọn (vd. Màu sắc × Size), mọi biến thể cùng giá với sản phẩm.
 * Bảng biến thể tự sinh đủ mọi tổ hợp; mỗi dòng có SKU (không bắt buộc, không trùng) và bật/tắt bán.
 * Tồn kho từng biến thể nhập ở trang Tồn kho. Bỏ một giá trị mà biến thể của nó còn hàng / đã có lịch sử kho → server từ
 * chối, lúc đó tắt bán thay vì xoá. Dùng bên trong Form của ProductForm (field: options, variantSettings).
 */
export function VariantsSection({ initial }: { initial?: AdminProductDetail }) {
  const form = Form.useFormInstance();
  const options: OptionField[] = Form.useWatch("options", form) ?? [];
  const usable = usableOptions(options);
  const combos = combinations(usable);

  // Tồn kho hiện có theo tổ hợp (chỉ biến thể đã lưu)
  const initialFields = initialVariantFields(initial);
  const stockByKey = new Map<string, number>();
  initial?.variants.forEach((v) => stockByKey.set(variantKeyOf(initialFields.options, v.values), v.stockQuantity));

  return (
    <Card
      title="Tuỳ chọn & biến thể"
      style={{ marginBottom: 20 }}
      extra={<Typography.Text type="secondary">Mọi biến thể cùng giá bán</Typography.Text>}
    >
      <Form.List name="options">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field, idx) => (
              <OptionRow key={field.key} fieldName={field.name} index={idx} onRemove={() => remove(field.name)} />
            ))}
            {fields.length < MAX_OPTIONS && (
              <Button
                type="dashed"
                block
                icon={<PlusOutlined />}
                onClick={() => add({ name: fields.length === 0 ? "Màu sắc" : "Size", values: [] })}
                style={{ marginBottom: 16 }}
              >
                {fields.length === 0 ? "Thêm tuỳ chọn (màu sắc, size…)" : "Thêm tuỳ chọn thứ 2"}
              </Button>
            )}
          </>
        )}
      </Form.List>

      <Form.Item name="optionsError" hidden>
        <Input />
      </Form.Item>
      <Form.Item shouldUpdate noStyle>
        {() => {
          const errs = form.getFieldError("optionsError");
          return errs.length ? (
            <Typography.Paragraph type="danger" style={{ marginBottom: 12 }}>
              {errs[0]}
            </Typography.Paragraph>
          ) : null;
        }}
      </Form.Item>

      {combos.length > MAX_VARIANTS ? (
        <Typography.Text type="danger">
          {combos.length} tổ hợp — tối đa {MAX_VARIANTS} biến thể. Hãy bớt giá trị.
        </Typography.Text>
      ) : (
        <Table
          size="small"
          pagination={false}
          rowKey={(combo) => comboKey(combo) || "default"}
          dataSource={combos}
          columns={[
            {
              title: "Biến thể",
              render: (_, combo) =>
                combo.length ? combo.map((v) => clean(v.value)).join(" / ") : <Typography.Text type="secondary">Mặc định (không có tuỳ chọn)</Typography.Text>,
            },
            {
              title: "SKU",
              width: 200,
              render: (_, combo) => (
                <Form.Item
                  name={["variantSettings", comboKey(combo), "sku"]}
                  style={{ margin: 0 }}
                  rules={[
                    { max: 64, message: "Tối đa 64 ký tự." },
                    { pattern: /^\s*([A-Za-z0-9._-]+\s*)?$/, message: "Chỉ chữ, số, . - _" },
                  ]}
                >
                  <Input placeholder="Không bắt buộc" maxLength={64} />
                </Form.Item>
              ),
            },
            {
              title: "Tồn kho",
              width: 90,
              align: "right",
              render: (_, combo) => {
                const stock = stockByKey.get(comboKey(combo));
                return stock === undefined ? <Tag>Mới</Tag> : stock;
              },
            },
            {
              title: "Đang bán",
              width: 90,
              align: "center",
              render: (_, combo) => (
                <Form.Item name={["variantSettings", comboKey(combo), "isActive"]} valuePropName="checked" initialValue={true} style={{ margin: 0 }}>
                  <Switch size="small" />
                </Form.Item>
              ),
            },
          ]}
        />
      )}
      <Typography.Paragraph type="secondary" style={{ margin: "12px 0 0" }}>
        Nhập / xuất kho từng biến thể ở trang Tồn kho. Thêm tuỳ chọn mới: tồn kho hiện có chuyển sang giá trị đầu tiên của
        tuỳ chọn đó. Biến thể đã có hàng không xoá được — hãy tắt bán.
      </Typography.Paragraph>
    </Card>
  );
}

/** Một tuỳ chọn: tên + các giá trị (thêm bằng Enter). */
function OptionRow({ fieldName, index, onRemove }: { fieldName: number; index: number; onRemove: () => void }) {
  const form = Form.useFormInstance();
  const [draft, setDraft] = useState("");

  const addValue = () => {
    const value = draft.trim();
    if (!value) return;
    const values: OptionValueField[] = form.getFieldValue(["options", fieldName, "values"]) ?? [];
    if (values.some((v) => clean(v.value).toLowerCase() === value.toLowerCase())) {
      form.setFields([{ name: ["options", fieldName, "values"], errors: [`"${value}" đã có.`] }]);
      return;
    }
    form.setFieldValue(["options", fieldName, "values"], [...values, { key: newKey(), value }]);
    form.validateFields([["options", fieldName, "values"]]).catch(() => undefined);
    setDraft("");
  };

  return (
    <div style={{ border: "1px solid var(--cv-border, #e5e7eb)", borderRadius: 8, padding: 12, marginBottom: 12 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
        <Form.Item
          name={[fieldName, "name"]}
          label={`Tuỳ chọn ${index + 1}`}
          style={{ flex: 1, marginBottom: 8 }}
          rules={[
            { required: true, whitespace: true, message: "Nhập tên tuỳ chọn, vd. Màu sắc." },
            { max: 50, message: "Tối đa 50 ký tự." },
            {
              validator: async (_, value?: string) => {
                const all: OptionField[] = form.getFieldValue("options") ?? [];
                const name = clean(value).toLowerCase();
                if (name && all.filter((o) => clean(o?.name).toLowerCase() === name).length > 1) throw new Error("Tên tuỳ chọn bị trùng.");
              },
            },
          ]}
        >
          <Input placeholder="VD: Màu sắc" maxLength={50} />
        </Form.Item>
        <Button type="text" danger icon={<DeleteOutlined />} onClick={onRemove} aria-label="Xoá tuỳ chọn" style={{ marginTop: 30 }} />
      </div>

      <Form.List
        name={[fieldName, "values"]}
        rules={[
          {
            validator: async (_, values: OptionValueField[] = []) => {
              if (!values.some((v) => clean(v?.value))) throw new Error("Cần ít nhất 1 giá trị.");
              if (values.length > 30) throw new Error("Tối đa 30 giá trị.");
              const seen = new Set<string>();
              for (const v of values) {
                const k = clean(v?.value).toLowerCase();
                if (k && seen.has(k)) throw new Error(`Giá trị "${clean(v.value)}" bị trùng.`);
                seen.add(k);
              }
            },
          },
        ]}
      >
        {(fields, { remove }, { errors }) => (
          <>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {fields.map((f) => (
                <Form.Item key={f.key} name={[f.name, "value"]} noStyle rules={[{ max: 100, message: "Tối đa 100 ký tự." }]}>
                  <Input
                    size="small"
                    style={{ width: 130 }}
                    maxLength={100}
                    suffix={<CloseOutlined style={{ fontSize: 10, cursor: "pointer" }} onClick={() => remove(f.name)} aria-label="Xoá giá trị" />}
                  />
                </Form.Item>
              ))}
              <Input
                size="small"
                style={{ width: 160 }}
                value={draft}
                maxLength={100}
                placeholder="Thêm giá trị, Enter"
                onChange={(e) => setDraft(e.target.value)}
                onPressEnter={(e) => {
                  e.preventDefault();
                  addValue();
                }}
                onBlur={addValue}
              />
            </div>
            <Form.ErrorList errors={errors} />
          </>
        )}
      </Form.List>
    </div>
  );
}
