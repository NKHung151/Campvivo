"use client";

import { useRouter } from "next/navigation";
import { App } from "antd";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ComboForm } from "@/components/admin/combo/ComboForm";
import { createCombo } from "@/services/admin/adminComboService";

/** /admin/combos/new — thêm combo (FR-15). */
export default function NewComboPage() {
  const router = useRouter();
  const { message } = App.useApp();

  return (
    <RequireRole roles={ADMIN_ONLY}>
      <ComboForm
        title="Thêm combo"
        submitText="Tạo combo"
        onSubmit={async (body) => {
          const created = await createCombo(body);
          message.success(`Đã tạo combo "${created.name}".`);
          router.push(`/admin/combos/${created.id}`);
        }}
      />
    </RequireRole>
  );
}
