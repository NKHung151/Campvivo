"use client";

import { Button, Card, Form, Input, Space } from "antd";
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import type { ProductSpec } from "@/types/api/catalog";

const isEmptyRow = (r?: Partial<ProductSpec>) => !r || (!(r.name ?? "").trim() && !(r.value ?? "").trim());

/**
 * Thông số sản phẩm: danh sách dòng "Tên: Giá trị" tự nhập (lưu JSON, giống nhau cho mọi loại hàng), hiện thành bảng
 * thông số ở trang sản phẩm. Dòng để trống cả hai ô sẽ bị bỏ khi lưu. Dùng bên trong Form của ProductForm (field: specs).
 */
export function SpecsSection() {
  const form = Form.useFormInstance();

  return (
    <Card title="Thông số kỹ thuật" style={{ marginBottom: 20 }}>
      <Form.List
        name="specs"
        rules={[
          {
            validator: async (_, rows: Partial<ProductSpec>[] = []) => {
              if (rows.filter((r) => !isEmptyRow(r)).length > 30) throw new Error("Tối đa 30 dòng thông số.");
            },
          },
        ]}
      >
        {(fields, { add, remove, move }, { errors }) => (
          <>
            {fields.map((field, idx) => (
              <div key={field.key} className="cv-spec-row">
                <Form.Item
                  name={[field.name, "name"]}
                  style={{ margin: 0 }}
                  rules={[
                    { max: 100, message: "Tối đa 100 ký tự." },
                    {
                      validator: async (_, value?: string) => {
                        const row: Partial<ProductSpec> = form.getFieldValue(["specs", field.name]) ?? {};
                        const name = (value ?? "").trim().toLowerCase();
                        if (!name && (row.value ?? "").trim()) throw new Error("Nhập tên thông số.");
                        const rows: Partial<ProductSpec>[] = form.getFieldValue("specs") ?? [];
                        if (name && rows.filter((r) => (r.name ?? "").trim().toLowerCase() === name).length > 1) {
                          throw new Error("Tên thông số bị trùng.");
                        }
                      },
                    },
                  ]}
                >
                  <Input placeholder="Tên, vd. Trọng lượng" maxLength={100} />
                </Form.Item>
                <Form.Item
                  name={[field.name, "value"]}
                  style={{ margin: 0 }}
                  dependencies={[["specs", field.name, "name"]]}
                  rules={[
                    { max: 500, message: "Tối đa 500 ký tự." },
                    {
                      validator: async (_, value?: string) => {
                        const row: Partial<ProductSpec> = form.getFieldValue(["specs", field.name]) ?? {};
                        if ((row.name ?? "").trim() && !(value ?? "").trim()) throw new Error("Nhập giá trị, hoặc xoá dòng này.");
                      },
                    },
                  ]}
                >
                  <Input placeholder="Giá trị, vd. 281 g" maxLength={500} />
                </Form.Item>
                <Space size={0}>
                  <Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={idx === 0} onClick={() => move(idx, idx - 1)} aria-label="Lên" />
                  <Button
                    type="text"
                    size="small"
                    icon={<ArrowDownOutlined />}
                    disabled={idx === fields.length - 1}
                    onClick={() => move(idx, idx + 1)}
                    aria-label="Xuống"
                  />
                  <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => remove(field.name)} aria-label="Xoá dòng" />
                </Space>
              </div>
            ))}
            <Form.ErrorList errors={errors} />
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ name: "", value: "" })}>
              Thêm thông số
            </Button>
          </>
        )}
      </Form.List>
    </Card>
  );
}
