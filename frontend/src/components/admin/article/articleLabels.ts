import type { ArticleCategory } from "@/types/api/common";

/**
 * Tên hiển thị của 3 loại bài (D-16). Tên tạm theo trang cửa hàng hiện có — nhóm chốt tên mới thì chỉ cần sửa ở đây.
 */
export const ARTICLE_CATEGORY: Record<ArticleCategory, { label: string; page: string; hint: string; color: string }> = {
  BLOG: { label: "Blog", page: "/blog", hint: "Mẹo, kinh nghiệm đi cắm trại, leo núi, kỹ năng an toàn", color: "green" },
  JOURNAL: { label: "Journal", page: "/journal", hint: "Hành trình, điểm đến, cung trekking, câu chuyện chuyến đi", color: "geekblue" },
  GUIDE: { label: "Guide", page: "/guides", hint: "Hướng dẫn chọn và dùng đúng từng loại hàng", color: "gold" },
};

export const ARTICLE_CATEGORIES = Object.keys(ARTICLE_CATEGORY) as ArticleCategory[];

export const fmtDate = (iso: string | null) =>
  iso ? new Date(iso).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" }) : "—";

export const fmtDateTime = (iso: string | null) =>
  iso
    ? new Date(iso).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" })
    : "—";
