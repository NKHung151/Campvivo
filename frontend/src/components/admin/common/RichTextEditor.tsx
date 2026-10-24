"use client";

import { useEffect, useRef, useState } from "react";
import { App, Button, Divider, Input, Popover, Space, Spin, Tooltip } from "antd";
import {
  BoldOutlined,
  CodeOutlined,
  FontSizeOutlined,
  ItalicOutlined,
  LinkOutlined,
  OrderedListOutlined,
  PictureOutlined,
  RedoOutlined,
  StrikethroughOutlined,
  UnderlineOutlined,
  UndoOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import { EditorContent, useEditor, useEditorState, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import { Placeholder } from "@tiptap/extensions";
import { getErrorMessage } from "@/lib/errorMessages";
import { uploadImage, type UploadFolder } from "@/services/admin/adminProductService";

const MAX_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];

/** Editor rỗng trả "<p></p>" — coi như không có nội dung. */
const toValue = (html: string) => (html === "<p></p>" ? "" : html);

/**
 * Trình soạn thảo trực quan (TipTap) cho mô tả sản phẩm / nội dung bài viết — dùng trong Form antd (value/onChange
 * là chuỗi HTML). Ảnh chèn vào được tải lên Cloudinary qua backend (nút ảnh, kéo-thả hoặc dán). Backend vẫn làm
 * sạch HTML khi lưu (HtmlSanitizer).
 */
export function RichTextEditor({
  value = "",
  onChange,
  folder = "products",
  placeholder = "Nhập nội dung…",
}: {
  value?: string;
  onChange?: (html: string) => void;
  folder?: UploadFolder;
  placeholder?: string;
}) {
  const { message } = App.useApp();
  const [htmlMode, setHtmlMode] = useState(false);
  const [uploading, setUploading] = useState(0);
  const fileInput = useRef<HTMLInputElement>(null);
  // Dùng trong editorProps (tạo 1 lần) nên phải đọc hàm mới nhất qua ref
  const uploadRef = useRef<(files: File[], at?: number) => void>(() => {});

  const editor = useEditor({
    immediatelyRender: false,
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3, 4] },
        link: { openOnClick: false, autolink: true, defaultProtocol: "https" },
      }),
      Image,
      Placeholder.configure({ placeholder }),
    ],
    content: value,
    onUpdate: ({ editor: e }) => onChange?.(toValue(e.getHTML())),
    editorProps: {
      attributes: { class: "cv-rte-content" },
      handlePaste: (_view, event) => {
        const files = Array.from(event.clipboardData?.files ?? []).filter((f) => f.type.startsWith("image/"));
        if (!files.length) return false;
        uploadRef.current(files);
        return true;
      },
      handleDrop: (view, event) => {
        const files = Array.from(event.dataTransfer?.files ?? []).filter((f) => f.type.startsWith("image/"));
        if (!files.length) return false;
        const pos = view.posAtCoords({ left: event.clientX, top: event.clientY })?.pos;
        uploadRef.current(files, pos);
        return true;
      },
    },
  });

  // Giá trị đổi từ bên ngoài (nạp dữ liệu sản phẩm, chế độ HTML) → cập nhật editor, không phát lại onChange
  useEffect(() => {
    if (editor && !editor.isDestroyed && value !== toValue(editor.getHTML())) {
      editor.commands.setContent(value || "", { emitUpdate: false });
    }
  }, [editor, value]);

  uploadRef.current = (files, at) => {
    if (!editor) return;
    for (const file of files) {
      if (!IMAGE_TYPES.includes(file.type)) {
        message.error(`${file.name}: chỉ chấp nhận ảnh JPG, PNG hoặc WEBP.`);
        continue;
      }
      if (file.size > MAX_BYTES) {
        message.error(`${file.name}: ảnh tối đa 5MB.`);
        continue;
      }
      setUploading((n) => n + 1);
      uploadImage(file, folder)
        .then((url) => {
          const chain = editor.chain().focus();
          (at !== undefined ? chain.insertContentAt(at, { type: "image", attrs: { src: url, alt: "" } }) : chain.setImage({ src: url, alt: "" })).run();
        })
        .catch((e) => message.error(`Tải ảnh thất bại: ${getErrorMessage(e)}`))
        .finally(() => setUploading((n) => n - 1));
    }
  };

  return (
    <div className="cv-rte">
      <Toolbar
        editor={editor}
        htmlMode={htmlMode}
        uploading={uploading > 0}
        onToggleHtml={() => setHtmlMode((v) => !v)}
        onPickImage={() => fileInput.current?.click()}
      />
      <input
        ref={fileInput}
        type="file"
        accept={IMAGE_TYPES.join(",")}
        multiple
        style={{ display: "none" }}
        onChange={(e) => {
          uploadRef.current(Array.from(e.target.files ?? []));
          e.target.value = "";
        }}
      />
      {htmlMode ? (
        <Input.TextArea
          className="cv-rte-html"
          value={value}
          onChange={(e) => onChange?.(e.target.value)}
          autoSize={{ minRows: 12, maxRows: 30 }}
          spellCheck={false}
        />
      ) : (
        <Spin spinning={uploading > 0} tip="Đang tải ảnh…">
          <EditorContent editor={editor} />
        </Spin>
      )}
    </div>
  );
}

function Toolbar({
  editor,
  htmlMode,
  uploading,
  onToggleHtml,
  onPickImage,
}: {
  editor: Editor | null;
  htmlMode: boolean;
  uploading: boolean;
  onToggleHtml: () => void;
  onPickImage: () => void;
}) {
  const [linkOpen, setLinkOpen] = useState(false);
  const [linkUrl, setLinkUrl] = useState("");
  const state = useEditorState({
    editor,
    selector: ({ editor: e }) =>
      e
        ? {
            bold: e.isActive("bold"),
            italic: e.isActive("italic"),
            underline: e.isActive("underline"),
            strike: e.isActive("strike"),
            h2: e.isActive("heading", { level: 2 }),
            h3: e.isActive("heading", { level: 3 }),
            bullet: e.isActive("bulletList"),
            ordered: e.isActive("orderedList"),
            quote: e.isActive("blockquote"),
            link: e.isActive("link"),
            canUndo: e.can().undo(),
            canRedo: e.can().redo(),
          }
        : null,
  });
  const off = !editor || htmlMode;
  const run = (fn: (e: Editor) => void) => () => editor && fn(editor);

  const btn = (title: string, icon: React.ReactNode, active: boolean | undefined, onClick: () => void, disabled = off) => (
    <Tooltip title={title} key={title}>
      <Button size="small" type={active ? "primary" : "text"} icon={icon} onClick={onClick} disabled={disabled} aria-label={title} />
    </Tooltip>
  );
  const textBtn = (title: string, label: string, active: boolean | undefined, onClick: () => void) => (
    <Tooltip title={title} key={title}>
      <Button size="small" type={active ? "primary" : "text"} onClick={onClick} disabled={off} aria-label={title}>
        {label}
      </Button>
    </Tooltip>
  );

  const applyLink = () => {
    if (!editor) return;
    const url = linkUrl.trim();
    const chain = editor.chain().focus().extendMarkRange("link");
    (url ? chain.setLink({ href: /^(https?:|mailto:)/i.test(url) ? url : `https://${url}` }) : chain.unsetLink()).run();
    setLinkOpen(false);
  };

  return (
    <div className="cv-rte-toolbar">
      <Space size={2} wrap split={<Divider type="vertical" style={{ margin: "0 2px" }} />}>
        <Space size={2}>
          {btn("In đậm (Ctrl+B)", <BoldOutlined />, state?.bold, run((e) => e.chain().focus().toggleBold().run()))}
          {btn("In nghiêng (Ctrl+I)", <ItalicOutlined />, state?.italic, run((e) => e.chain().focus().toggleItalic().run()))}
          {btn("Gạch chân (Ctrl+U)", <UnderlineOutlined />, state?.underline, run((e) => e.chain().focus().toggleUnderline().run()))}
          {btn("Gạch ngang", <StrikethroughOutlined />, state?.strike, run((e) => e.chain().focus().toggleStrike().run()))}
        </Space>
        <Space size={2}>
          {textBtn("Tiêu đề lớn", "H2", state?.h2, run((e) => e.chain().focus().toggleHeading({ level: 2 }).run()))}
          {textBtn("Tiêu đề nhỏ", "H3", state?.h3, run((e) => e.chain().focus().toggleHeading({ level: 3 }).run()))}
          {btn("Đoạn văn thường", <FontSizeOutlined />, false, run((e) => e.chain().focus().setParagraph().run()))}
        </Space>
        <Space size={2}>
          {btn("Danh sách chấm", <UnorderedListOutlined />, state?.bullet, run((e) => e.chain().focus().toggleBulletList().run()))}
          {btn("Danh sách số", <OrderedListOutlined />, state?.ordered, run((e) => e.chain().focus().toggleOrderedList().run()))}
          {textBtn("Trích dẫn", "❝", state?.quote, run((e) => e.chain().focus().toggleBlockquote().run()))}
        </Space>
        <Space size={2}>
          <Popover
            open={linkOpen}
            onOpenChange={(open) => {
              if (open && editor) setLinkUrl((editor.getAttributes("link").href as string | undefined) ?? "");
              setLinkOpen(open && !off);
            }}
            trigger="click"
            content={
              <Space.Compact style={{ width: 320 }}>
                <Input
                  autoFocus
                  placeholder="https://… (để trống để bỏ liên kết)"
                  value={linkUrl}
                  onChange={(e) => setLinkUrl(e.target.value)}
                  onPressEnter={applyLink}
                />
                <Button type="primary" onClick={applyLink}>
                  OK
                </Button>
              </Space.Compact>
            }
          >
            <Tooltip title="Chèn / sửa liên kết">
              <Button size="small" type={state?.link ? "primary" : "text"} icon={<LinkOutlined />} disabled={off} aria-label="Liên kết" />
            </Tooltip>
          </Popover>
          {btn("Chèn ảnh (JPG, PNG, WEBP ≤ 5MB) — có thể kéo-thả hoặc dán ảnh vào nội dung", <PictureOutlined />, false, onPickImage, off || uploading)}
        </Space>
        <Space size={2}>
          {btn("Hoàn tác (Ctrl+Z)", <UndoOutlined />, false, run((e) => e.chain().focus().undo().run()), off || !state?.canUndo)}
          {btn("Làm lại (Ctrl+Y)", <RedoOutlined />, false, run((e) => e.chain().focus().redo().run()), off || !state?.canRedo)}
        </Space>
        {btn(htmlMode ? "Quay lại soạn thảo" : "Xem / sửa mã HTML", <CodeOutlined />, htmlMode, onToggleHtml, !editor)}
      </Space>
    </div>
  );
}
