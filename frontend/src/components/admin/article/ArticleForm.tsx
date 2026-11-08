"use client";
/* eslint-disable @next/next/no-img-element -- ảnh trong bản xem trước (Cloudinary) hiển thị nguyên */

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { App, Button, Card, Col, Descriptions, Form, Image, Input, Modal, Radio, Row, Space, Tag, Typography, Upload } from "antd";
import type { UploadProps } from "antd";
import { DeleteOutlined, EyeOutlined, UploadOutlined } from "@ant-design/icons";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { SLUG_PATTERN, toSlug } from "@/lib/slug";
import { uploadImage } from "@/services/admin/adminProductService";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { RichTextEditor } from "@/components/admin/common/RichTextEditor";
import { ARTICLE_CATEGORIES, ARTICLE_CATEGORY, fmtDateTime } from "@/components/admin/article/articleLabels";
import type { ArticleCategory } from "@/types/api/common";
import type { AdminArticleDetail, ArticleRequest } from "@/types/api/article";

interface FormValues {
  title: string;
  slug?: string;
  summary?: string;
  content?: string;
  thumbnailUrl?: string | null;
  category?: ArticleCategory;
}

const ACCEPT = "image/jpeg,image/png,image/webp";
/** Tóm tắt nên ngắn để vừa thẻ bài; cột cho phép tới 1000 ký tự. */
const SUMMARY_HINT = 250;
const hasText = (html?: string) => Boolean(html && (html.replace(/<[^>]*>|&nbsp;| /g, " ").trim() || html.includes("<img")));

/**
 * Form soạn bài viết (FR-21b). "Lưu nháp" lưu được khi còn thiếu; "Xuất bản" bắt buộc có tóm tắt, ảnh đại diện, nội dung
 * (backend kiểm tra lại). Bài đã đăng: "Lưu thay đổi" giữ trạng thái đăng, "Gỡ xuống nháp" ẩn khỏi cửa hàng.
 */
export function ArticleForm({
  initial,
  onSubmit,
}: {
  initial?: AdminArticleDetail;
  onSubmit: (body: ArticleRequest) => Promise<void>;
}) {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState<"draft" | "publish" | null>(null);
  const [preview, setPreview] = useState(false);
  const dirty = useRef(false);
  const title = Form.useWatch("title", form) ?? "";
  const summary: string = Form.useWatch("summary", form) ?? "";
  const category: ArticleCategory | undefined = Form.useWatch("category", form);
  const published = initial?.isPublished ?? false;

  const initialValues = useMemo<FormValues>(
    () =>
      initial
        ? {
            title: initial.title,
            slug: initial.slug,
            summary: initial.summary ?? undefined,
            content: initial.content,
            thumbnailUrl: initial.thumbnailUrl,
            category: initial.category,
          }
        : { title: "", content: "" },
    [initial]
  );

  // Cảnh báo khi đóng tab / tải lại trang mà còn thay đổi chưa lưu
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => {
      if (!dirty.current) return;
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, []);

  const save = async (publish: boolean) => {
    let v: FormValues;
    try {
      v = await form.validateFields();
    } catch {
      message.error("Vui lòng kiểm tra lại các ô được đánh dấu.");
      return;
    }
    if (publish) {
      const missing: { name: keyof FormValues; errors: string[] }[] = [];
      if (!v.summary?.trim()) missing.push({ name: "summary", errors: ["Cần nhập tóm tắt trước khi xuất bản."] });
      if (!v.thumbnailUrl) missing.push({ name: "thumbnailUrl", errors: ["Cần chọn ảnh đại diện trước khi xuất bản."] });
      if (!hasText(v.content)) missing.push({ name: "content", errors: ["Cần viết nội dung trước khi xuất bản."] });
      if (missing.length) {
        form.setFields(missing);
        form.scrollToField(missing[0].name, { behavior: "smooth", block: "center" });
        message.error("Bài chưa đủ để xuất bản — có thể lưu nháp trước.");
        return;
      }
    }
    setSaving(publish ? "publish" : "draft");
    try {
      await onSubmit({
        title: v.title.trim(),
        slug: v.slug?.trim() || null,
        summary: v.summary?.trim() || null,
        content: v.content ?? "",
        thumbnailUrl: v.thumbnailUrl || null,
        category: v.category!,
        isPublished: publish,
      });
      dirty.current = false;
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
      setSaving(null);
    }
  };

  const unpublish = () =>
    modal.confirm({
      title: "Gỡ bài xuống nháp?",
      content: "Bài sẽ không còn hiển thị trên cửa hàng. Ngày đăng đầu tiên được giữ lại khi đăng lại.",
      okText: "Gỡ xuống",
      cancelText: "Huỷ",
      onOk: () => save(false),
    });

  const busy = saving !== null;
  const actions = (
    <>
      <Link href="/admin/articles">
        <Button disabled={false}>Huỷ</Button>
      </Link>
      <Button icon={<EyeOutlined />} onClick={() => setPreview(true)} disabled={false}>
        Xem trước
      </Button>
      {published ? (
        <>
          <Button onClick={unpublish} loading={saving === "draft"} disabled={busy}>
            Gỡ xuống nháp
          </Button>
          <Button type="primary" onClick={() => save(true)} loading={saving === "publish"} disabled={busy}>
            Lưu thay đổi
          </Button>
        </>
      ) : (
        <>
          <Button onClick={() => save(false)} loading={saving === "draft"} disabled={busy}>
            Lưu nháp
          </Button>
          <Button type="primary" onClick={() => save(true)} loading={saving === "publish"} disabled={busy}>
            Xuất bản
          </Button>
        </>
      )}
    </>
  );

  return (
    <Form<FormValues>
      form={form}
      layout="vertical"
      initialValues={initialValues}
      onValuesChange={() => (dirty.current = true)}
      requiredMark={false}
      disabled={busy}
    >
      <PageHeader
        sticky
        breadcrumb={[{ label: "Bài viết", href: "/admin/articles" }, { label: initial ? "Sửa bài" : "Viết bài mới" }]}
        title={initial ? initial.title : "Viết bài mới"}
        tags={initial && (published ? <span className="cv-status is-on">Đã đăng</span> : <span className="cv-status is-off">Nháp</span>)}
        actions={actions}
      />
      <Row gutter={20}>
        <Col xs={24} xl={16}>
          <Card title="Nội dung" style={{ marginBottom: 20 }}>
            <Form.Item
              name="title"
              label="Tiêu đề"
              rules={[
                { required: true, whitespace: true, message: "Vui lòng nhập tiêu đề." },
                { min: 5, max: 255, message: "Tiêu đề phải từ 5 đến 255 ký tự." },
              ]}
            >
              <Input size="large" placeholder="VD: Chọn túi ngủ theo nhiệt độ" maxLength={255} showCount />
            </Form.Item>
            <Form.Item
              name="summary"
              label="Tóm tắt"
              extra={
                summary.length > SUMMARY_HINT
                  ? `Nên ngắn hơn ${SUMMARY_HINT} ký tự để hiện trọn trên thẻ bài.`
                  : "1–2 câu, hiện ở thẻ bài, trang danh sách và kết quả tìm kiếm. Bắt buộc khi xuất bản."
              }
              rules={[{ max: 1000, message: "Tóm tắt tối đa 1000 ký tự." }]}
            >
              <Input.TextArea autoSize={{ minRows: 2, maxRows: 5 }} maxLength={1000} showCount placeholder="Bài viết giúp khách biết điều gì?" />
            </Form.Item>
            <Form.Item name="content" label="Nội dung bài" style={{ marginBottom: 0 }} rules={[{ max: 200_000, message: "Nội dung quá dài." }]}>
              <RichTextEditor folder="articles" placeholder="Viết nội dung bài… Dùng H2/H3 để chia mục, chèn ảnh bằng nút ảnh hoặc kéo-thả." />
            </Form.Item>
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          {initial && (
            <Card title="Thông tin" style={{ marginBottom: 20 }}>
              <Descriptions column={1} size="small" colon={false} labelStyle={{ width: 110 }}>
                <Descriptions.Item label="Tác giả">{initial.authorName}</Descriptions.Item>
                <Descriptions.Item label="Ngày đăng">{fmtDateTime(initial.publishedAt)}</Descriptions.Item>
                <Descriptions.Item label="Tạo lúc">{fmtDateTime(initial.createdAt)}</Descriptions.Item>
                <Descriptions.Item label="Sửa lần cuối">{fmtDateTime(initial.updatedAt)}</Descriptions.Item>
              </Descriptions>
            </Card>
          )}

          <Card title="Loại bài" style={{ marginBottom: 20 }}>
            <Form.Item name="category" style={{ marginBottom: 0 }} rules={[{ required: true, message: "Vui lòng chọn loại bài viết." }]}>
              <Radio.Group className="cv-article-category">
                {ARTICLE_CATEGORIES.map((c) => (
                  <Radio key={c} value={c}>
                    <span className="cv-article-category-name">
                      {ARTICLE_CATEGORY[c].label} <Typography.Text type="secondary">{ARTICLE_CATEGORY[c].page}</Typography.Text>
                    </span>
                    <span className="cv-article-category-hint">{ARTICLE_CATEGORY[c].hint}</span>
                  </Radio>
                ))}
              </Radio.Group>
            </Form.Item>
          </Card>

          <Card title="Ảnh đại diện" style={{ marginBottom: 20 }}>
            <Form.Item name="thumbnailUrl" style={{ marginBottom: 0 }} extra="Ảnh ngang (16:9), rộng từ 1200px. Bắt buộc khi xuất bản.">
              <ThumbnailField />
            </Form.Item>
          </Card>

          <Card title="Đường dẫn" style={{ marginBottom: 20 }}>
            <Form.Item
              name="slug"
              style={{ marginBottom: 0 }}
              extra={initial ? "Để trống = giữ đường dẫn cũ." : "Để trống = tự tạo từ tiêu đề."}
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
              <Input addonBefore={category ? `${ARTICLE_CATEGORY[category].page}/` : "/"} placeholder={initial ? initial.slug : toSlug(title) || "tu-dong-tao"} />
            </Form.Item>
          </Card>
        </Col>
      </Row>

      <Modal open={preview} onCancel={() => setPreview(false)} footer={null} width={860} title="Xem trước bài viết" destroyOnClose>
        <Form.Item noStyle shouldUpdate>
          {() => <ArticlePreview values={form.getFieldsValue(true) as FormValues} />}
        </Form.Item>
      </Modal>
    </Form>
  );
}

/** Bản xem trước gần giống trang chi tiết: loại bài, tiêu đề, tóm tắt, ảnh đại diện, nội dung. */
function ArticlePreview({ values }: { values: FormValues }) {
  return (
    <article className="cv-article-preview">
      {values.category && (
        <Tag bordered={false} color={ARTICLE_CATEGORY[values.category].color}>
          {ARTICLE_CATEGORY[values.category].label}
        </Tag>
      )}
      <h1>{values.title || <i>Chưa có tiêu đề</i>}</h1>
      {values.summary && <p className="cv-article-preview-summary">{values.summary}</p>}
      {values.thumbnailUrl && <img src={values.thumbnailUrl} alt="" className="cv-article-preview-cover" />}
      {values.content ? (
        <div className="cv-rte-content" dangerouslySetInnerHTML={{ __html: values.content }} />
      ) : (
        <Typography.Text type="secondary">Chưa có nội dung.</Typography.Text>
      )}
    </article>
  );
}

/** Một ảnh đại diện: tải lên Cloudinary (thư mục articles) hoặc dán link. value/onChange: URL. */
function ThumbnailField({ value, onChange }: { value?: string | null; onChange?: (url: string | null) => void }) {
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
      const url = await uploadImage(file as File, "articles");
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
        <Image src={value} alt="Ảnh đại diện" className="cv-article-cover" />
        <Space wrap>
          <Upload accept={ACCEPT} showUploadList={false} beforeUpload={beforeUpload} customRequest={customRequest}>
            <Button icon={<UploadOutlined />} loading={uploading}>
              Đổi ảnh
            </Button>
          </Upload>
          <Button icon={<DeleteOutlined />} onClick={() => onChange?.(null)}>
            Bỏ ảnh
          </Button>
        </Space>
      </Space>
    );
  }
  return (
    <Space direction="vertical" style={{ width: "100%" }}>
      <Upload accept={ACCEPT} showUploadList={false} beforeUpload={beforeUpload} customRequest={customRequest}>
        <Button icon={<UploadOutlined />} loading={uploading}>
          Tải ảnh lên (JPG, PNG, WEBP · ≤ 5MB)
        </Button>
      </Upload>
      <Space.Compact style={{ width: "100%" }}>
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
