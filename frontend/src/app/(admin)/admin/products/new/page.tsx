"use client";

import { useRouter } from "next/navigation";
import { App } from "antd";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ProductForm } from "@/components/admin/catalog/ProductForm";
import { createProduct } from "@/services/admin/adminProductService";

/** /admin/products/new — thêm sản phẩm (FR-06). */
export default function NewProductPage() {
  const router = useRouter();
  const { message } = App.useApp();

  return (
    <RequireRole roles={ADMIN_ONLY}>
      <ProductForm
        title="Thêm sản phẩm"
        submitText="Tạo sản phẩm"
        onSubmit={async (body) => {
          const created = await createProduct(body);
          message.success(`Đã tạo sản phẩm "${created.name}".`);
          router.push(`/admin/products/${created.id}`);
        }}
      />
    </RequireRole>
  );
}
