"use client";

import { useRouter } from "next/navigation";
import { App } from "antd";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ArticleForm } from "@/components/admin/article/ArticleForm";
import { createArticle } from "@/services/admin/adminArticleService";

/** /admin/articles/new — viết bài mới (FR-21b). */
export default function NewArticlePage() {
  const router = useRouter();
  const { message } = App.useApp();

  return (
    <RequireRole roles={ADMIN_ONLY}>
      <ArticleForm
        onSubmit={async (body) => {
          const created = await createArticle(body);
          message.success(created.isPublished ? `Đã đăng bài "${created.title}".` : `Đã lưu nháp "${created.title}".`);
          router.push(`/admin/articles/${created.id}`);
        }}
      />
    </RequireRole>
  );
}
