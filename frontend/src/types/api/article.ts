import type { ArticleCategory, PageQuery } from "./common";

export type { ArticleSummary } from "./catalog";

/** Bài viết — docs/02-ky-thuat/api.md mục 4.3 (public) và 4.10 (admin). FR-21. */

/** `GET /articles/{slug}` — chỉ bài đã xuất bản. */
export interface ArticleDetail {
  id: number;
  title: string;
  slug: string;
  summary: string | null;
  thumbnailUrl: string | null;
  category: ArticleCategory;
  publishedAt: string;
  content: string;
  authorName: string;
}

export interface ArticleQuery extends PageQuery {
  category?: ArticleCategory;
}

/* ───────────── Admin ───────────── */

export type AdminArticleSort = "UPDATED_DESC" | "CREATED_DESC" | "TITLE_ASC";

export interface AdminArticleQuery extends PageQuery {
  q?: string;
  category?: ArticleCategory;
  isPublished?: boolean;
  sort?: AdminArticleSort;
}

export interface AdminArticleSummary {
  id: number;
  title: string;
  slug: string;
  summary: string | null;
  thumbnailUrl: string | null;
  category: ArticleCategory;
  isPublished: boolean;
  /** Ngày xuất bản lần đầu; null = chưa từng đăng. Gỡ rồi đăng lại vẫn giữ. */
  publishedAt: string | null;
  authorName: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminArticleDetail extends AdminArticleSummary {
  content: string;
}

/** POST/PUT `/admin/articles`. Xuất bản (`isPublished`) bắt buộc có tóm tắt, ảnh đại diện, nội dung. */
export interface ArticleRequest {
  title: string;
  /** null/"" → tạo mới thì sinh từ tiêu đề; sửa thì giữ slug cũ. */
  slug: string | null;
  summary: string | null;
  content: string;
  thumbnailUrl: string | null;
  category: ArticleCategory;
  isPublished: boolean;
}
