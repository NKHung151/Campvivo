"use client";

import Link from "next/link";
import { Space, Typography } from "antd";
import { RightOutlined } from "@ant-design/icons";

/**
 * Thanh tiêu đề trang admin: đường dẫn nhỏ, tiêu đề lớn (+ nhãn trạng thái), nút thao tác bên phải.
 * `sticky`: dính trên cùng khi cuộn (dùng cho trang form có nút Lưu).
 */
export function PageHeader({
  breadcrumb = [],
  title,
  tags,
  actions,
  sticky,
}: {
  breadcrumb?: { label: string; href?: string }[];
  title: React.ReactNode;
  tags?: React.ReactNode;
  actions?: React.ReactNode;
  sticky?: boolean;
}) {
  return (
    <div className={`cv-page-header${sticky ? " is-sticky" : ""}`}>
      <div className="cv-page-header-main">
        {breadcrumb.length > 0 && (
          <div className="cv-page-crumbs">
            {breadcrumb.map((b, i) => (
              <span key={b.label + i}>
                {i > 0 && <RightOutlined className="cv-page-crumb-sep" />}
                {b.href ? <Link href={b.href}>{b.label}</Link> : b.label}
              </span>
            ))}
          </div>
        )}
        <Space size={10} align="center" wrap>
          <Typography.Title level={3} className="cv-page-title">
            {title}
          </Typography.Title>
          {tags}
        </Space>
      </div>
      {actions && (
        <Space size={8} wrap className="cv-page-actions">
          {actions}
        </Space>
      )}
    </div>
  );
}
