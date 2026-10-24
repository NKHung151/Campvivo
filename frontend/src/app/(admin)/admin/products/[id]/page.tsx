"use client";

import { useCallback, useEffect, useState } from "react";
import { App, Button, Card, Result, Skeleton, Space } from "antd";
import { ExportOutlined } from "@ant-design/icons";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ProductForm } from "@/components/admin/catalog/ProductForm";
import { getProduct, updateProduct } from "@/services/admin/adminProductService";
import type { AdminProductDetail } from "@/types/api/catalog";

/** /admin/products/[id] — sửa sản phẩm (FR-06). */
export default function EditProductPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const { message } = App.useApp();
  const [product, setProduct] = useState<AdminProductDetail | null>(null);
  const [error, setError] = useState<ApiError | Error | null>(null);
  // Đổi key sau khi lưu → form khởi tạo lại từ dữ liệu server (slug/ảnh đã chuẩn hoá)
  const [version, setVersion] = useState(0);

  const load = useCallback(() => {
    setError(null);
    setProduct(null);
    if (!Number.isInteger(id) || id <= 0) {
      setError(new ApiError({ status: 404, errorCode: "NOT_FOUND", message: "Không tìm thấy sản phẩm." }));
      return;
    }
    getProduct(id)
      .then(setProduct)
      .catch((e) => setError(e instanceof Error ? e : new Error(getErrorMessage(e))));
  }, [id]);

  useEffect(load, [load]);

  let body: React.ReactNode;
  if (error) {
    const notFound = error instanceof ApiError && error.errorCode === "NOT_FOUND";
    body = (
      <Card>
        <Result
          status={notFound ? "404" : "error"}
          title={notFound ? "Không tìm thấy sản phẩm" : "Không tải được sản phẩm"}
          subTitle={notFound ? "Sản phẩm không tồn tại." : getErrorMessage(error)}
          extra={
            <Space>
              <Button href="/admin/products">Về danh sách</Button>
              {!notFound && (
                <Button type="primary" onClick={load}>
                  Thử lại
                </Button>
              )}
            </Space>
          }
        />
      </Card>
    );
  } else if (!product) {
    body = (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  } else {
    body = (
      <>
        <ProductForm
          key={version}
          initial={product}
          title={product.name}
          tags={product.isActive ? <span className="cv-status is-on">Đang bán</span> : <span className="cv-status is-off">Đã ẩn</span>}
          extraActions={
            product.isActive && (
              <Button href={`/products/${product.slug}`} target="_blank" icon={<ExportOutlined />}>
                Xem trên cửa hàng
              </Button>
            )
          }
          submitText="Lưu thay đổi"
          onSubmit={async (req) => {
            const saved = await updateProduct(product.id, req);
            setProduct(saved);
            setVersion((v) => v + 1);
            message.success("Đã lưu sản phẩm.");
          }}
        />
      </>
    );
  }

  return <RequireRole roles={ADMIN_ONLY}>{body}</RequireRole>;
}
