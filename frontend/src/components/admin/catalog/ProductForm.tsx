"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { App, AutoComplete, Button, Card, Col, Form, Input, InputNumber, Row, Select, Typography } from "antd";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { SLUG_PATTERN, toSlug } from "@/lib/slug";
import { getBrands, getCategoryTree } from "@/services/admin/adminProductService";
import { ImageListField } from "@/components/admin/common/ImageListField";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { RichTextEditor } from "@/components/admin/common/RichTextEditor";
import { CategoryCascadeSelect } from "@/components/admin/catalog/CategoryCascadeSelect";
import { SpecsSection } from "@/components/admin/catalog/SpecsSection";
import type { AdminProductDetail, AdminProductImage, CategoryNode, ProductRequest, ProductSpec } from "@/types/api/catalog";

interface FormValues {
  name: string;
  slug?: string;
  shortDescription?: string;
  description?: string;
  categoryId?: number;
  brand?: string;
  price?: number;
  isActive: boolean;
  specs: Partial<ProductSpec>[];
  images: AdminProductImage[];
}

const vndFormatter = (v: number | string | undefined) => (v === undefined || v === "" ? "" : `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, "."));
const vndParser = (v: string | undefined) => Number((v ?? "").replace(/\D/g, "")) as unknown as 0;

/** Lỗi backend dạng "specs" → ô tương ứng trong form. */
const FIELD_MAP: Record<string, (keyof FormValues)[]> = { specs: ["specs"] };

/**
 * Form thêm/sửa sản phẩm (FR-06). Không có biến thể (D-24): mỗi màu/size là một sản phẩm riêng.
 * Tồn kho không sửa ở đây — nhập kho ở trang Tồn kho.
 * Lỗi từ backend: `fieldErrors` hiện dưới đúng ô, lỗi khác hiện thông báo.
 */
export function ProductForm({
  initial,
  title,
  tags,
  extraActions,
  submitText,
  onSubmit,
}: {
  initial?: AdminProductDetail;
  title: React.ReactNode;
  tags?: React.ReactNode;
  /** Nút thêm cạnh Huỷ/Lưu, vd. "Xem trên cửa hàng". */
  extraActions?: React.ReactNode;
  submitText: string;
  onSubmit: (body: ProductRequest) => Promise<void>;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [tree, setTree] = useState<CategoryNode[] | null>(null);
  const [brands, setBrands] = useState<string[]>([]);
  const name = Form.useWatch("name", form) ?? "";

  useEffect(() => {
    getCategoryTree()
      .then(setTree)
      .catch((e) => {
        setTree([]);
        message.error(`Không tải được danh mục: ${getErrorMessage(e)}`);
      });
    getBrands()
      .then((b) => setBrands(b.map((x) => x.name)))
      .catch(() => setBrands([]));
  }, [message]);

  const initialValues = useMemo<FormValues>(
    () =>
      initial
        ? {
            name: initial.name,
            slug: initial.slug,
            shortDescription: initial.shortDescription ?? undefined,
            description: initial.description ?? undefined,
            categoryId: initial.categoryId,
            brand: initial.brand ?? undefined,
            price: initial.price,
            isActive: initial.isActive,
            specs: initial.specs,
            images: initial.images,
          }
        : { name: "", isActive: true, specs: [], images: [] },
    [initial],
  );

  const submit = async (v: FormValues) => {
    setSubmitting(true);
    try {
      await onSubmit({
        name: v.name.trim(),
        slug: v.slug?.trim() || undefined,
        shortDescription: v.shortDescription?.trim() || null,
        description: v.description?.trim() || null,
        brand: v.brand?.trim() || null,
        price: v.price!,
        categoryId: v.categoryId!,
        isActive: v.isActive,
        specs: (v.specs ?? [])
          .map((s) => ({ name: (s.name ?? "").trim(), value: (s.value ?? "").trim() }))
          .filter((s) => s.name && s.value),
        images: v.images,
      });
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
        form.setFields(
          Object.entries(e.fieldErrors).map(([field, msg]) => ({ name: (FIELD_MAP[field]?.[0] ?? field) as keyof FormValues, errors: [msg] })),
        );
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
        breadcrumb={[{ label: "Sản phẩm", href: "/admin/products" }, { label: initial ? "Sửa sản phẩm" : "Thêm sản phẩm" }]}
        title={title}
        tags={tags}
        actions={
          <>
            {extraActions}
            <Link href="/admin/products">
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
          <Card title="Thông tin chung" style={{ marginBottom: 20 }}>
            <Form.Item
              name="name"
              label="Tên sản phẩm"
              rules={[
                { required: true, whitespace: true, message: "Vui lòng nhập tên sản phẩm." },
                { min: 2, max: 255, message: "Tên sản phẩm phải từ 2 đến 255 ký tự." },
              ]}
            >
              <Input placeholder="VD: Lều cắm trại 2 người chống nước" maxLength={255} showCount />
            </Form.Item>
            <Form.Item name="shortDescription" label="Mô tả ngắn" rules={[{ max: 500, message: "Mô tả ngắn tối đa 500 ký tự." }]}>
              <Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} maxLength={500} showCount placeholder="1–2 câu nổi bật hiện ngay dưới tên sản phẩm" />
            </Form.Item>
            <Form.Item name="description" label="Mô tả chi tiết" style={{ marginBottom: 0 }} rules={[{ max: 50_000, message: "Mô tả quá dài." }]}>
              <RichTextEditor folder="products" placeholder="Giới thiệu, đặc điểm nổi bật, hướng dẫn sử dụng…" />
            </Form.Item>
          </Card>

          <Card title="Hình ảnh" style={{ marginBottom: 20 }}>
            <Form.Item
              name="images"
              noStyle
              rules={[
                {
                  validator: (_, list: AdminProductImage[] = []) =>
                    list.length <= 10 ? Promise.resolve() : Promise.reject(new Error("Tối đa 10 ảnh cho một sản phẩm.")),
                },
              ]}
            >
              <ImageListField folder="products" />
            </Form.Item>
            <Form.Item shouldUpdate noStyle>
              {() => {
                const errs = form.getFieldError("images");
                return errs.length ? <Typography.Text type="danger">{errs[0]}</Typography.Text> : null;
              }}
            </Form.Item>
          </Card>

          <SpecsSection />
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

          <Card title="Giá bán" style={{ marginBottom: 20 }}>
            <Form.Item
              name="price"
              style={{ marginBottom: 0 }}
              rules={[
                { required: true, message: "Vui lòng nhập giá bán." },
                { type: "number", min: 1, message: "Giá bán phải lớn hơn 0." },
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
                placeholder="450.000"
              />
            </Form.Item>
          </Card>

          <Card title="Tổ chức" style={{ marginBottom: 20 }}>
            <Form.Item name="categoryId" label="Danh mục" rules={[{ required: true, message: "Vui lòng chọn danh mục." }]}>
              <CategoryCascadeSelect tree={tree ?? []} loading={tree === null} />
            </Form.Item>
            <Form.Item name="brand" label="Thương hiệu" style={{ marginBottom: 0 }} rules={[{ max: 100, message: "Thương hiệu tối đa 100 ký tự." }]}>
              <AutoComplete
                options={brands.map((b) => ({ value: b }))}
                filterOption={(input, opt) => (opt?.value ?? "").toLowerCase().includes(input.toLowerCase())}
                placeholder="VD: Naturehike"
                allowClear
              />
            </Form.Item>
          </Card>

          {initial && (
            <Card title="Tồn kho" style={{ marginBottom: 20 }}>
              <div className="cv-mini-stats">
                <div>
                  <span>Tồn</span>
                  <b>{initial.stockQuantity}</b>
                </div>
                <div>
                  <span>Giữ chỗ</span>
                  <b>{initial.reservedQuantity}</b>
                </div>
                <div>
                  <span>Còn bán</span>
                  <b className={initial.availableQuantity > 0 ? "is-good" : "is-bad"}>{initial.availableQuantity}</b>
                </div>
              </div>
            </Card>
          )}

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
              <Input addonBefore="/products/" placeholder={initial ? initial.slug : toSlug(name) || "tu-dong-tao"} />
            </Form.Item>
          </Card>
        </Col>
      </Row>
    </Form>
  );
}
