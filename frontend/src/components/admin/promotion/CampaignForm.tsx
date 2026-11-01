"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { App, Button, Card, Col, DatePicker, Form, Image, Input, InputNumber, Radio, Row, Select, Space, Typography, Upload } from "antd";
import type { UploadProps } from "antd";
import { DeleteOutlined, UploadOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { SLUG_PATTERN, toSlug } from "@/lib/slug";
import { uploadImage } from "@/services/admin/adminProductService";
import { findOverlaps } from "@/services/admin/adminCampaignService";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { RichTextEditor } from "@/components/admin/common/RichTextEditor";
import { CampaignProductPicker, MAX_CAMPAIGN_PRODUCTS, type CampaignProductRow } from "@/components/admin/promotion/CampaignProductPicker";
import type { DiscountType } from "@/types/api/common";
import type { AdminCampaignDetail, CampaignOverlap, CampaignRequest } from "@/types/api/promotion";

interface FormValues {
  name: string;
  slug?: string;
  description?: string;
  bannerUrl?: string | null;
  discountType: DiscountType;
  discountValue?: number;
  period?: [Dayjs, Dayjs];
  isActive: boolean;
  products: CampaignProductRow[];
}

/** Lỗi backend → ô tương ứng trong form (startDate/endDate gộp vào ô "Thời gian"). */
const FIELD_MAP: Record<string, keyof FormValues> = { startDate: "period", endDate: "period", productIds: "products" };
const ACCEPT = "image/jpeg,image/png,image/webp";
const vndFormatter = (v: number | string | undefined) => (v === undefined || v === "" ? "" : `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, "."));
const vndParser = (v: string | undefined) => Number((v ?? "").replace(/\D/g, "")) as unknown as 0;

/**
 * Form tạo/sửa chiến dịch giảm giá (FR-17, BR-6). Chỉ áp cho sản phẩm (không combo). Sản phẩm đã thuộc chiến dịch khác
 * trùng thời gian chỉ bị cảnh báo, vẫn lưu được (khách được mức giảm lớn nhất).
 */
export function CampaignForm({
  initial,
  title,
  tags,
  submitText,
  onSubmit,
}: {
  initial?: AdminCampaignDetail;
  title: React.ReactNode;
  tags?: React.ReactNode;
  submitText: string;
  onSubmit: (body: CampaignRequest) => Promise<void>;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [overlaps, setOverlaps] = useState<CampaignOverlap[]>([]);
  const name = Form.useWatch("name", form) ?? "";
  const discountType: DiscountType | undefined = Form.useWatch("discountType", form);
  const discountValue: number | undefined = Form.useWatch("discountValue", form);
  const period: [Dayjs, Dayjs] | undefined = Form.useWatch("period", form);
  const products: CampaignProductRow[] = Form.useWatch("products", form) ?? [];

  const initialValues = useMemo<FormValues>(
    () =>
      initial
        ? {
            name: initial.name,
            slug: initial.slug,
            description: initial.description ?? undefined,
            bannerUrl: initial.bannerUrl,
            discountType: initial.discountType,
            discountValue: initial.discountValue,
            period: [dayjs(initial.startDate), dayjs(initial.endDate)],
            isActive: initial.isActive,
            products: initial.products.map((p) => ({
              id: p.id,
              name: p.name,
              slug: p.slug,
              imageUrl: p.primaryImageUrl,
              price: p.price,
              isActive: p.isActive,
            })),
          }
        : {
            name: "",
            discountType: "PERCENTAGE",
            period: [dayjs().startOf("minute"), dayjs().add(7, "day").endOf("day")],
            isActive: true,
            products: [],
          },
    [initial]
  );

  // Cảnh báo chồng chiến dịch: hỏi lại khi danh sách sản phẩm / thời gian đổi (trễ 400ms)
  const productKey = products.map((p) => p.id).join(",");
  const startIso = period?.[0]?.format();
  const endIso = period?.[1]?.format();
  useEffect(() => {
    if (!productKey || !startIso || !endIso || !dayjs(endIso).isAfter(startIso)) {
      setOverlaps([]);
      return;
    }
    const t = setTimeout(() => {
      findOverlaps({ productIds: productKey.split(",").map(Number), startDate: startIso, endDate: endIso, excludeId: initial?.id })
        .then(setOverlaps)
        .catch(() => setOverlaps([]));
    }, 400);
    return () => clearTimeout(t);
  }, [productKey, startIso, endIso, initial?.id]);

  const submit = async (v: FormValues) => {
    setSubmitting(true);
    try {
      await onSubmit({
        name: v.name.trim(),
        slug: v.slug?.trim() || null,
        description: v.description?.trim() || null,
        bannerUrl: v.bannerUrl || null,
        discountType: v.discountType,
        discountValue: v.discountValue!,
        startDate: v.period![0].format(),
        endDate: v.period![1].format(),
        isActive: v.isActive,
        productIds: v.products.map((p) => p.id),
      });
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
        form.setFields(Object.entries(e.fieldErrors).map(([f, msg]) => ({ name: FIELD_MAP[f] ?? (f as keyof FormValues), errors: [msg] })));
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

  const discount = discountType && discountValue ? { discountType, discountValue } : null;

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
        breadcrumb={[{ label: "Chiến dịch giảm giá", href: "/admin/campaigns" }, { label: initial ? "Sửa chiến dịch" : "Tạo chiến dịch" }]}
        title={title}
        tags={tags}
        actions={
          <>
            <Link href="/admin/campaigns">
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
            title="Sản phẩm áp dụng"
            extra={products.length > 0 && <Typography.Text type="secondary">{products.length} sản phẩm</Typography.Text>}
            style={{ marginBottom: 20 }}
          >
            <Form.Item
              name="products"
              noStyle
              rules={[
                {
                  validator: async (_, rows: CampaignProductRow[] = []) => {
                    if (rows.length > MAX_CAMPAIGN_PRODUCTS) throw new Error(`Tối đa ${MAX_CAMPAIGN_PRODUCTS} sản phẩm.`);
                  },
                },
              ]}
            >
              <CampaignProductPicker discount={discount} overlaps={overlaps} />
            </Form.Item>
            <Form.Item shouldUpdate noStyle>
              {() => {
                const errs = form.getFieldError("products");
                return errs.length ? <Typography.Text type="danger">{errs[0]}</Typography.Text> : null;
              }}
            </Form.Item>
            <Typography.Paragraph type="secondary" style={{ margin: "12px 0 0", fontSize: 12 }}>
              Chiến dịch chỉ áp cho sản phẩm, không áp cho combo. Giá trên cửa hàng đổi ngay khi chiến dịch đang trong thời gian chạy.
            </Typography.Paragraph>
          </Card>

          <Card title="Thông tin chung" style={{ marginBottom: 20 }}>
            <Form.Item
              name="name"
              label="Tên chiến dịch"
              rules={[
                { required: true, whitespace: true, message: "Vui lòng nhập tên chiến dịch." },
                { min: 2, max: 255, message: "Tên chiến dịch phải từ 2 đến 255 ký tự." },
              ]}
            >
              <Input placeholder="VD: Xả hàng cuối mùa" maxLength={255} showCount />
            </Form.Item>
            <Form.Item name="bannerUrl" label="Banner">
              <BannerField />
            </Form.Item>
            <Form.Item name="description" label="Mô tả" style={{ marginBottom: 0 }} rules={[{ max: 50_000, message: "Mô tả quá dài." }]}>
              <RichTextEditor folder="campaigns" placeholder="Nội dung chương trình, điều kiện áp dụng…" />
            </Form.Item>
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
                  { value: "on", label: <span className="cv-status is-on">Bật</span> },
                  { value: "off", label: <span className="cv-status is-off">Tắt (không áp giá)</span> },
                ]}
              />
            </Form.Item>
          </Card>

          <Card title="Mức giảm" style={{ marginBottom: 20 }}>
            <Form.Item name="discountType" rules={[{ required: true }]}>
              <Radio.Group
                optionType="button"
                buttonStyle="solid"
                options={[
                  { value: "PERCENTAGE", label: "Theo %" },
                  { value: "FIXED_AMOUNT", label: "Số tiền" },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="discountValue"
              style={{ marginBottom: 0 }}
              rules={[
                { required: true, message: "Vui lòng nhập giá trị giảm." },
                discountType === "PERCENTAGE"
                  ? { type: "number", min: 1, max: 100, message: "Từ 1 đến 100%." }
                  : { type: "number", min: 1, message: "Phải lớn hơn 0." },
              ]}
            >
              {discountType === "PERCENTAGE" ? (
                <InputNumber<number> size="large" style={{ width: "100%" }} min={1} max={100} precision={0} addonAfter="%" />
              ) : (
                <InputNumber<number> size="large" style={{ width: "100%" }} min={1} precision={0} formatter={vndFormatter} parser={vndParser} addonAfter="₫" />
              )}
            </Form.Item>
          </Card>

          <Card title="Thời gian" style={{ marginBottom: 20 }}>
            <Form.Item
              name="period"
              style={{ marginBottom: 0 }}
              rules={[
                { required: true, message: "Vui lòng chọn thời gian." },
                {
                  validator: (_, v?: [Dayjs, Dayjs]) =>
                    !v || v[1].isAfter(v[0]) ? Promise.resolve() : Promise.reject(new Error("Thời gian kết thúc phải sau thời gian bắt đầu.")),
                },
              ]}
            >
              <DatePicker.RangePicker showTime={{ format: "HH:mm" }} format="DD/MM/YYYY HH:mm" style={{ width: "100%" }} />
            </Form.Item>
          </Card>

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
              <Input addonBefore="/promotions/" placeholder={initial ? initial.slug : toSlug(name) || "tu-dong-tao"} />
            </Form.Item>
          </Card>
        </Col>
      </Row>
    </Form>
  );
}

/** Một ảnh banner: tải lên Cloudinary (thư mục campaigns) hoặc dán link. value/onChange: URL. */
function BannerField({ value, onChange }: { value?: string | null; onChange?: (url: string | null) => void }) {
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(false);
  const [urlInput, setUrlInput] = useState("");

  const beforeUpload: UploadProps["beforeUpload"] = (file) => {
    if (!ACCEPT.split(",").includes(file.type)) {
      message.error("Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP.");
      return Upload.LIST_IGNORE;
    }
    if (file.size > 5 * 1024 * 1024) {
      message.error("Ảnh vượt quá 5MB.");
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  const customRequest: UploadProps["customRequest"] = async ({ file, onSuccess, onError }) => {
    setUploading(true);
    try {
      const url = await uploadImage(file as File, "campaigns");
      onChange?.(url);
      onSuccess?.(url);
    } catch (e) {
      message.error(getErrorMessage(e));
      onError?.(e as Error);
    } finally {
      setUploading(false);
    }
  };

  if (value) {
    return (
      <Space direction="vertical" style={{ width: "100%" }}>
        <Image src={value} alt="Banner" style={{ maxHeight: 180, objectFit: "cover", borderRadius: 6 }} />
        <Button icon={<DeleteOutlined />} onClick={() => onChange?.(null)}>
          Bỏ banner
        </Button>
      </Space>
    );
  }
  return (
    <Space direction="vertical" style={{ width: "100%" }}>
      <Upload accept={ACCEPT} showUploadList={false} beforeUpload={beforeUpload} customRequest={customRequest}>
        <Button icon={<UploadOutlined />} loading={uploading}>
          Tải ảnh banner (JPG, PNG, WEBP · ≤ 5MB)
        </Button>
      </Upload>
      <Space.Compact style={{ width: "100%", maxWidth: 560 }}>
        <Input placeholder="Hoặc dán link ảnh" value={urlInput} onChange={(e) => setUrlInput(e.target.value)} />
        <Button
          onClick={() => {
            const url = urlInput.trim();
            if (!/^https?:\/\/\S+$/.test(url)) return message.error("Link ảnh phải bắt đầu bằng http:// hoặc https://.");
            onChange?.(url);
            setUrlInput("");
          }}
        >
          Dùng link
        </Button>
      </Space.Compact>
    </Space>
  );
}
