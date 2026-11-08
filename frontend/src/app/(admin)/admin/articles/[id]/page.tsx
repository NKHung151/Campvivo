"use client";

import { useCallback, useEffect, useState } from "react";
import { App, Button, Card, Result, Skeleton, Space } from "antd";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ArticleForm } from "@/components/admin/article/ArticleForm";
import { getArticle, updateArticle } from "@/services/admin/adminArticleService";
import type { AdminArticleDetail } from "@/types/api/article";

/** /admin/articles/[id] — sửa bài viết, đăng / gỡ (FR-21b). */
export default function EditArticlePage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const { message } = App.useApp();
  const [article, setArticle] = useState<AdminArticleDetail | null>(null);
  const [error, setError] = useState<ApiError | Error | null>(null);
  // Đổi key sau khi lưu → form khởi tạo lại từ dữ liệu server
  const [version, setVersion] = useState(0);

  const load = useCallback(() => {
    setError(null);
    setArticle(null);
    if (!Number.isInteger(id) || id <= 0) {
      setError(new ApiError({ status: 404, errorCode: "NOT_FOUND", message: "Không tìm thấy bài viết." }));
      return;
    }
    getArticle(id)
      .then(setArticle)
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
          title={notFound ? "Không tìm thấy bài viết" : "Không tải được bài viết"}
          subTitle={notFound ? "Bài viết không tồn tại hoặc đã bị xoá." : getErrorMessage(error)}
          extra={
            <Space>
              <Button href="/admin/articles">Về danh sách</Button>
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
  } else if (!article) {
    body = (
      <Card>
        <Skeleton active paragraph={{ rows: 10 }} />
      </Card>
    );
  } else {
    body = (
      <ArticleForm
        key={version}
        initial={article}
        onSubmit={async (req) => {
          const saved = await updateArticle(article.id, req);
          const wasPublished = article.isPublished;
          setArticle(saved);
          setVersion((v) => v + 1);
          message.success(
            saved.isPublished === wasPublished ? "Đã lưu bài viết." : saved.isPublished ? "Đã xuất bản bài viết." : "Đã gỡ bài xuống nháp."
          );
        }}
      />
    );
  }

  return <RequireRole roles={ADMIN_ONLY}>{body}</RequireRole>;
}
