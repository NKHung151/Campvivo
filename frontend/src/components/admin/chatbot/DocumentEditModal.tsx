"use client";

import { useEffect, useState } from "react";
import { Alert, App, Form, Input, Modal, Switch } from "antd";
import { getErrorMessage } from "@/lib/errorMessages";
import { updateDocument } from "@/services/admin/adminChatbotService";
import type { ChatbotDocument } from "@/types/api/chatbot";

interface Values {
  title: string;
  description?: string;
  isActive: boolean;
}

/** Sửa tiêu đề, mô tả, bật/tắt tài liệu (tắt = bot không dùng nữa, có hiệu lực ngay). */
export function DocumentEditModal({
  doc,
  onClose,
  onSaved,
}: {
  doc: ChatbotDocument | null;
  onClose: () => void;
  onSaved: (doc: ChatbotDocument) => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<Values>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (doc) {
      form.setFieldsValue({ title: doc.title, description: doc.description ?? "", isActive: doc.isActive });
      setError(null);
    }
  }, [doc, form]);

  const submit = async () => {
    if (!doc) return;
    const values = await form.validateFields();
    setSaving(true);
    setError(null);
    try {
      const saved = await updateDocument(doc.id, {
        title: values.title.trim(),
        description: values.description?.trim() || null,
        isActive: values.isActive,
      });
      message.success("Đã lưu tài liệu.");
      onSaved(saved);
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal open={doc !== null} title="Sửa tài liệu" okText="Lưu" cancelText="Huỷ" confirmLoading={saving} onOk={submit} onCancel={onClose}>
      <Form form={form} layout="vertical">
        <Form.Item
          name="title"
          label="Tiêu đề"
          rules={[
            { required: true, whitespace: true, message: "Vui lòng nhập tiêu đề." },
            { max: 255, message: "Tiêu đề tối đa 255 ký tự." },
          ]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="description" label="Mô tả" rules={[{ max: 1000, message: "Mô tả tối đa 1000 ký tự." }]}>
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item name="isActive" label="Cho chatbot dùng tài liệu này" valuePropName="checked">
          <Switch checkedChildren="Bật" unCheckedChildren="Tắt" />
        </Form.Item>
      </Form>
      {error && <Alert type="error" showIcon message={error} />}
    </Modal>
  );
}
