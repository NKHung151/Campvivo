"use client";

import { useEffect, useState } from "react";
import { Alert, App, Form, Input, Modal, Typography, Upload } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import { getErrorMessage } from "@/lib/errorMessages";
import { replaceDocumentFile, uploadDocument } from "@/services/admin/adminChatbotService";
import type { ChatbotDocument } from "@/types/api/chatbot";
import { ACCEPT, checkFile, fileSize, MAX_FILE_BYTES } from "./documentUi";

interface Props {
  open: boolean;
  /** Có `replace` = thay file của tài liệu này; không có = tải tài liệu mới. */
  replace?: ChatbotDocument | null;
  onClose: () => void;
  onDone: (doc: ChatbotDocument) => void;
}

/** Tải tài liệu mới hoặc thay file của tài liệu đã có. Xử lý (đọc, chia đoạn, embed) chạy nền sau khi gửi. */
export function DocumentUploadModal({ open, replace, onClose, onDone }: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ title?: string; description?: string }>();
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
      setFile(null);
      setFileError(null);
      setError(null);
    }
  }, [open, form]);

  const submit = async () => {
    if (!file) {
      setFileError("Vui lòng chọn tài liệu.");
      return;
    }
    const values = replace ? {} : await form.validateFields();
    setSaving(true);
    setError(null);
    try {
      const doc = replace
        ? await replaceDocumentFile(replace.id, file)
        : await uploadDocument(file, values.title, values.description);
      message.success(replace ? "Đã thay file, đang nạp lại tài liệu." : "Đã tải lên, đang xử lý tài liệu.");
      onDone(doc);
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={replace ? `Thay file: ${replace.title}` : "Tải tài liệu cho chatbot"}
      okText={replace ? "Thay file" : "Tải lên"}
      cancelText="Huỷ"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      {replace && (
        <Typography.Paragraph type="secondary">
          Bot sẽ tạm không dùng tài liệu này cho tới khi nạp xong file mới. File hiện tại: {replace.fileName}.
        </Typography.Paragraph>
      )}
      <Upload.Dragger
        accept={ACCEPT}
        maxCount={1}
        showUploadList={false}
        beforeUpload={(f) => {
          const problem = checkFile(f);
          setFileError(problem);
          setFile(problem ? null : f);
          if (!problem && !replace && !form.getFieldValue("title")) {
            form.setFieldValue("title", f.name.replace(/\.[^.]+$/, ""));
          }
          return false;
        }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">{file ? `${file.name} (${fileSize(file.size)})` : "Bấm hoặc kéo thả tài liệu vào đây"}</p>
        <p className="ant-upload-hint">
          PDF, Word (.docx), .txt, .md — tối đa {MAX_FILE_BYTES / (1024 * 1024)}MB. PDF scan (chỉ có ảnh) không đọc được.
        </p>
      </Upload.Dragger>
      {fileError && <Typography.Text type="danger">{fileError}</Typography.Text>}

      {!replace && (
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="title"
            label="Tiêu đề"
            extra="Bot trích nguồn theo tiêu đề này, vd. (Theo: Chính sách đổi trả)."
            rules={[{ max: 255, message: "Tiêu đề tối đa 255 ký tự." }]}
          >
            <Input placeholder="Mặc định lấy theo tên file" />
          </Form.Item>
          <Form.Item name="description" label="Mô tả" rules={[{ max: 1000, message: "Mô tả tối đa 1000 ký tự." }]}>
            <Input.TextArea rows={2} placeholder="Ghi chú cho admin (không bắt buộc)" />
          </Form.Item>
        </Form>
      )}
      {error && <Alert type="error" showIcon message={error} style={{ marginTop: 12 }} />}
    </Modal>
  );
}
