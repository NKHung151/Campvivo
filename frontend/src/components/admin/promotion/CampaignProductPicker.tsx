"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Alert, App, Button, Empty, Image, Select, Tag, Tooltip, Typography } from "antd";
import { CloseOutlined, SearchOutlined, WarningOutlined } from "@ant-design/icons";
import { vnd } from "@/lib/format";
import { campaignPrice } from "@/lib/promotion";
import { listProducts } from "@/services/admin/adminProductService";
import type { AdminProductSummary } from "@/types/api/catalog";
import type { DiscountType } from "@/types/api/common";
import type { CampaignOverlap } from "@/types/api/promotion";

export const MAX_CAMPAIGN_PRODUCTS = 500;

export interface CampaignProductRow {
  id: number;
  name: string;
  slug: string;
  imageUrl: string | null;
  price: number;
  isActive: boolean;
}

/**
 * Danh sách sản phẩm áp dụng chiến dịch + ô tìm để thêm (chọn nhiều, chỉ sản phẩm — không combo). value/onChange:
 * CampaignProductRow[]. Hiện giá gốc → giá dự kiến theo mức giảm đang nhập, và cảnh báo sản phẩm đã thuộc chiến dịch
 * khác trùng thời gian (`overlaps`, không chặn lưu — BR-6 lấy mức giảm lớn nhất).
 */
export function CampaignProductPicker({
  value = [],
  onChange,
  discount,
  overlaps,
}: {
  value?: CampaignProductRow[];
  onChange?: (rows: CampaignProductRow[]) => void;
  discount: { discountType: DiscountType; discountValue: number } | null;
  overlaps: CampaignOverlap[];
}) {
  const { message } = App.useApp();
  const [options, setOptions] = useState<AdminProductSummary[]>([]);
  const [searching, setSearching] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const chosen = new Set(value.map((r) => r.id));
  const overlapOf = new Map<number, string[]>();
  overlaps.forEach((o) => overlapOf.set(o.productId, [...(overlapOf.get(o.productId) ?? []), o.campaignName]));

  const search = (q: string) => {
    clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      setSearching(true);
      listProducts({ q: q.trim() || undefined, limit: 30, sort: "UPDATED_DESC" })
        .then((res) => setOptions(res.data))
        .catch(() => setOptions([]))
        .finally(() => setSearching(false));
    }, 300);
  };

  useEffect(() => {
    search("");
    return () => clearTimeout(timer.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const add = (id: number) => {
    const p = options.find((o) => o.id === id);
    if (!p || chosen.has(id)) return;
    if (value.length >= MAX_CAMPAIGN_PRODUCTS) {
      message.warning(`Tối đa ${MAX_CAMPAIGN_PRODUCTS} sản phẩm.`);
      return;
    }
    onChange?.([...value, { id: p.id, name: p.name, slug: p.slug, imageUrl: p.primaryImageUrl, price: p.price, isActive: p.isActive }]);
  };

  return (
    <div className="cv-combo-items">
      {overlaps.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={`${overlapOf.size} sản phẩm đã thuộc chiến dịch khác trong cùng thời gian`}
          description="Vẫn lưu được. Khi chồng nhiều chiến dịch, khách được hưởng mức giảm lớn nhất (không cộng dồn)."
        />
      )}
      {value.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có sản phẩm — tìm và thêm sản phẩm áp dụng" />
      ) : (
        value.map((r) => {
          const others = overlapOf.get(r.id);
          return (
            <div key={r.id} className="cv-combo-item">
              {r.imageUrl ? <Image src={r.imageUrl} alt={r.name} width={56} height={56} className="cv-prod-thumb" /> : <div className="cv-img-empty">—</div>}
              <div className="cv-combo-item-main">
                <Link href={`/admin/products/${r.id}`} target="_blank" className="cv-prod-name">
                  {r.name}
                </Link>
                <div className="cv-combo-item-meta">
                  {!r.isActive && (
                    <Tooltip title="Sản phẩm đang ẩn — không hiện trên trang chiến dịch">
                      <Tag color="orange" bordered={false}>
                        Đang ẩn
                      </Tag>
                    </Tooltip>
                  )}
                  {others && (
                    <Tooltip title={`Cũng thuộc: ${others.join(", ")}`}>
                      <Tag color="gold" bordered={false} icon={<WarningOutlined />}>
                        Trùng {others.length} chiến dịch
                      </Tag>
                    </Tooltip>
                  )}
                </div>
              </div>
              <div className="cv-combo-item-total">
                {discount ? (
                  <>
                    <div>{vnd(campaignPrice(r.price, discount), " ₫")}</div>
                    <Typography.Text delete type="secondary" style={{ fontSize: 12 }}>
                      {vnd(r.price, " ₫")}
                    </Typography.Text>
                  </>
                ) : (
                  vnd(r.price, " ₫")
                )}
              </div>
              <Button
                type="text"
                size="small"
                icon={<CloseOutlined />}
                onClick={() => onChange?.(value.filter((x) => x.id !== r.id))}
                aria-label={`Bỏ ${r.name}`}
              />
            </div>
          );
        })
      )}
      <Select<number>
        showSearch
        value={null}
        suffixIcon={<SearchOutlined />}
        placeholder="＋ Thêm sản phẩm — gõ tên hoặc thương hiệu (không dấu được)"
        filterOption={false}
        onSearch={search}
        onSelect={add}
        loading={searching}
        notFoundContent={searching ? "Đang tìm…" : "Không có sản phẩm phù hợp"}
        style={{ width: "100%", marginTop: 12 }}
        options={options.map((p) => ({ value: p.id, label: p.name, data: p, disabled: chosen.has(p.id) }))}
        optionRender={(o) => {
          const p = (o.data as { data: AdminProductSummary }).data;
          return (
            <div className="cv-combo-opt">
              {p.primaryImageUrl ? (
                // eslint-disable-next-line @next/next/no-img-element -- ảnh Cloudinary nhỏ trong danh sách gợi ý
                <img src={p.primaryImageUrl} alt="" width={36} height={36} />
              ) : (
                <span className="cv-combo-opt-noimg" />
              )}
              <div className="cv-combo-opt-info">
                <div className="cv-combo-opt-name">{p.name}</div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {p.brand ? `${p.brand} · ` : ""}
                  {vnd(p.price, " ₫")}
                </Typography.Text>
              </div>
              {chosen.has(p.id) && <Tag color="green">Đã thêm</Tag>}
            </div>
          );
        }}
      />
    </div>
  );
}
