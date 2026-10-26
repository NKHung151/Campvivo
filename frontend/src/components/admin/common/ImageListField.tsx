"use client";

import { useRef, useState } from "react";
import { App, Button, Image, Input, Space, Tag, Tooltip, Upload } from "antd";
import type { UploadProps } from "antd";
import { ArrowLeftOutlined, ArrowRightOutlined, DeleteOutlined, LinkOutlined, StarFilled, StarOutlined, UploadOutlined } from "@ant-design/icons";
import { getErrorMessage } from "@/lib/errorMessages";
import { uploadImage, type UploadFolder } from "@/services/admin/adminProductService";
import type { AdminProductImage } from "@/types/api/catalog";

const MAX_IMAGES = 10;
const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPT = "image/jpeg,image/png,image/webp";

/** Đánh lại thứ tự theo vị trí và bảo đảm có đúng 1 ảnh chính khi danh sách không rỗng. */
function normalize(list: AdminProductImage[]): AdminProductImage[] {
  const hasPrimary = list.some((i) => i.isPrimary);
  return list.map((img, idx) => ({ ...img, displayOrder: idx, isPrimary: hasPrimary ? img.isPrimary : idx === 0 }));
}

/**
 * Quản lý danh sách ảnh (dùng trong Form antd: value/onChange): upload lên Cloudinary qua backend hoặc
 * dán URL, chọn ảnh chính, đổi thứ tự, xoá. Ảnh đầu tiên được chọn làm ảnh chính nếu chưa chọn.
 */
export function ImageListField({
  value = [],
  onChange,
  folder = "products",
}: {
  value?: AdminProductImage[];
  onChange?: (v: AdminProductImage[]) => void;
  folder?: UploadFolder;
}) {
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(0);
  const [urlInput, setUrlInput] = useState("");

  // Bản danh sách mới nhất: cập nhật ngay khi đổi (không chờ render) để nhiều ảnh upload xong gần
  // như cùng lúc không ghi đè nhau.
  const latest = useRef(value);
  latest.current = value;
  const set = (list: AdminProductImage[]) => {
    const next = normalize(list);
    latest.current = next;
    onChange?.(next);
  };
  const full = value.length + uploading >= MAX_IMAGES;

  const add = (url: string) => {
    const cur = latest.current;
    if (cur.some((i) => i.url === url)) {
      message.warning("Ảnh này đã có trong danh sách.");
      return;
    }
    set([...cur, { url, isPrimary: false, displayOrder: cur.length }]);
  };

  const beforeUpload: UploadProps["beforeUpload"] = (file) => {
    if (!ACCEPT.split(",").includes(file.type)) {
      message.error(`${file.name}: chỉ chấp nhận ảnh JPG, PNG hoặc WEBP.`);
      return Upload.LIST_IGNORE;
    }
    if (file.size > MAX_BYTES) {
      message.error(`${file.name}: ảnh vượt quá 5MB.`);
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  const customRequest: UploadProps["customRequest"] = async ({ file, onSuccess, onError }) => {
    setUploading((n) => n + 1);
    try {
      const url = await uploadImage(file as File, folder);
      add(url);
      onSuccess?.(url);
    } catch (e) {
      message.error(getErrorMessage(e));
      onError?.(e as Error);
    } finally {
      setUploading((n) => n - 1);
    }
  };

  const addUrl = () => {
    const url = urlInput.trim();
    if (!/^https?:\/\/\S+$/.test(url)) {
      message.error("Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.");
      return;
    }
    add(url);
    setUrlInput("");
  };

  const move = (idx: number, delta: number) => {
    const next = [...value];
    const [img] = next.splice(idx, 1);
    next.splice(idx + delta, 0, img);
    set(next);
  };

  return (
    <div>
      <div className="cv-img-grid">
        {value.map((img, idx) => (
          <div key={img.url} className={`cv-img-item${img.isPrimary ? " is-primary" : ""}`}>
            <Image src={img.url} alt={`Ảnh ${idx + 1}`} width={112} height={112} style={{ objectFit: "cover" }} />
            {img.isPrimary && (
              <Tag color="green" className="cv-img-badge">
                Ảnh chính
              </Tag>
            )}
            <Space size={0} className="cv-img-actions">
              <Tooltip title="Đặt làm ảnh chính">
                <Button
                  size="small"
                  type="text"
                  icon={img.isPrimary ? <StarFilled style={{ color: "#faad14" }} /> : <StarOutlined />}
                  onClick={() => set(value.map((i, k) => ({ ...i, isPrimary: k === idx })))}
                />
              </Tooltip>
              <Tooltip title="Lên trước">
                <Button size="small" type="text" icon={<ArrowLeftOutlined />} disabled={idx === 0} onClick={() => move(idx, -1)} />
              </Tooltip>
              <Tooltip title="Ra sau">
                <Button
                  size="small"
                  type="text"
                  icon={<ArrowRightOutlined />}
                  disabled={idx === value.length - 1}
                  onClick={() => move(idx, 1)}
                />
              </Tooltip>
              <Tooltip title="Xoá ảnh">
                <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => set(value.filter((_, k) => k !== idx))} />
              </Tooltip>
            </Space>
          </div>
        ))}
        {!full && (
          <Upload accept={ACCEPT} multiple showUploadList={false} beforeUpload={beforeUpload} customRequest={customRequest}>
            <button type="button" className="cv-img-add">
              <UploadOutlined style={{ fontSize: 20 }} />
              <div>{uploading > 0 ? `Đang tải ${uploading} ảnh…` : "Thêm ảnh"}</div>
              {uploading === 0 && <div className="cv-img-add-hint">JPG, PNG, WEBP · ≤ 5MB</div>}
            </button>
          </Upload>
        )}
      </div>
      <Space.Compact style={{ width: "100%", maxWidth: 560, marginTop: 12 }}>
        <Input
          prefix={<LinkOutlined />}
          placeholder="Hoặc dán link ảnh"
          value={urlInput}
          onChange={(e) => setUrlInput(e.target.value)}
          onPressEnter={(e) => {
            e.preventDefault();
            addUrl();
          }}
          disabled={full}
        />
        <Button onClick={addUrl} disabled={full || !urlInput.trim()}>
          Thêm
        </Button>
      </Space.Compact>
    </div>
  );
}
