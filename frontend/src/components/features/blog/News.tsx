/* eslint-disable @next/next/no-img-element */
import type { Article, ArticleCard, LinkItem } from "@/types/shop";
import { articleHref } from "@/lib/format";
import { SmartLink } from "@/components/ui/SmartLink";
import { ArticleFilters } from "@/components/features/blog/ArticleFilters";
import { DEAD } from "@/lib/dead-link";

export const PER_PAGE = 9;

/** "29/05/2026" from either list-card dates or detail "Ngày cập nhật 11/08/2021 01:32 PM". */
const shortDate = (d: string) => (d.match(/\d{2}\/\d{2}\/\d{4}/) ?? [d])[0];
const shortViews = (v: string) => v.replace(/lượt xem/i, "Lượt xem");

function NewsBreadcrumb({ items, extraClass = "" }: { items: LinkItem[]; extraClass?: string }) {
  return (
    <div id="Breadcrumb" className={`container${extraClass}`}>
      <div className="road">
        {items.map((b, i) => (
          <SmartLink key={b.href + i} className={`lv1${i ? " arrow" : ""}`} href={b.href} title={b.name}>
            {b.name}
          </SmartLink>
        ))}
      </div>
    </div>
  );
}

export function ArticleList({
  title,
  articles,
  page,
  filters,
  basePath,
  perPage = PER_PAGE,
  breadcrumbs,
  hrefFor = articleHref,
}: {
  title: string;
  articles: ArticleCard[];
  page: number;
  filters: { title: string; options: { name: string; href: string | null }[] }[];
  basePath: string;
  perPage?: number;
  /** Defaults to Trang chủ › <title>. */
  breadcrumbs?: LinkItem[];
  /** Article URL builder (blog by default, journal for Outdoor Journal). */
  hrefFor?: (slug: string) => string;
}) {
  const pages = Math.max(1, Math.ceil(articles.length / perPage));
  const items = articles.slice((page - 1) * perPage, page * perPage);
  const pageHref = (n: number) => (n === 1 ? basePath : `${basePath}/page/${n}`);
  return (
    <>
      <NewsBreadcrumb
        items={
          breadcrumbs ?? [
            { name: "Trang chủ", href: "/" },
            { name: title, href: basePath },
          ]
        }
      />
      <div className="container">
        <div className="tour_heading_1">
          <h1>{title} </h1> <span>({articles.length.toLocaleString("de-DE")} kết quả)</span>
        </div>
        <div className="tour_category">
          <ArticleFilters filters={filters} />
          <div className="tour_col_r">
            <div className="tour_list">
              {items.map((a) => (
                <SmartLink className="item" href={hrefFor(a.slug)} title={a.title} key={a.slug}>
                  {" "}
                  <div className="khungAnhCrop">
                    <img loading="lazy" alt={a.title} width={300} src={a.img} />
                  </div>{" "}
                  <div className="info">
                    <h2 className="title">{a.title}</h2>
                    {a.date && (
                      <div className="datetime">
                        {shortDate(a.date)} - {shortViews(a.views)}
                      </div>
                    )}
                    <div className="desc">{a.excerpt}</div>
                  </div>
                </SmartLink>
              ))}
            </div>
            {pages > 1 && (
              <div className="SplitPages">
                {Array.from({ length: pages }, (_, i) => i + 1).map((n) =>
                  n === page ? (
                    <a className="current" key={n}>
                      {n}
                    </a>
                  ) : (
                    <SmartLink className="other" href={pageHref(n)} key={n}>
                      {String(n)}
                    </SmartLink>
                  ),
                )}
                {page < pages && <SmartLink className="next" title="Next" href={pageHref(page + 1)} />}
                {page < pages && <SmartLink className="last" href={pageHref(pages)} />}
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  );
}

/** `wrapperClass` picks the page skin: NewsDetail (blog) or ServiceDetail (Outdoor Journal). */
export function ArticleDetail({ a, wrapperClass = "NewsDetail" }: { a: Article; wrapperClass?: string }) {
  return (
    <>
      <NewsBreadcrumb items={a.breadcrumbs} extraClass=" container2" />
      <div className={wrapperClass}>
        <div className="container container2">
          <h1 className="title">{a.title}</h1>
          <div className="totalview">
            <span>{a.date.startsWith("Ngày") ? a.date : `Ngày cập nhật ${a.date}`}</span> - <span>{a.views}</span>
          </div>
          <div className="contentview TextSize" dangerouslySetInnerHTML={{ __html: a.html }} />
          <div id="CommonAddthisNewsDetail">
            <span>Chia sẻ bài viết:</span>
            <a rel="noreferrer" href={DEAD} data-dead="" className="share_bt facebook">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M14 9V7c0-.8.5-1 1-1h2V3h-3c-2.8 0-4 1.9-4 4v2H8v3h2v9h4v-9h3l.5-3H14z" />
              </svg>
            </a>
            <a rel="noreferrer" href={DEAD} data-dead="" className="share_bt twitter">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M18 3h3l-7 8 8 10h-6l-5-6-5 6H3l7-8L2 3h6l4 5z" />
              </svg>
            </a>
            <div className="cb" />
          </div>
          {a.related.length > 0 && (
            <div id="SubNewsOtherItems">
              <div className="head">Bài viết cùng chuyên mục</div>
              <div className="tour_list">
                {a.related.map((r) => (
                  <SmartLink className="item" href={r.href} title={r.title} key={r.href}>
                    {" "}
                    <div className="khungAnhCrop">
                      <img loading="lazy" alt={r.title} width={250} src={r.img} />
                    </div>{" "}
                    <div className="info">
                      <h2 className="title">{r.title}</h2>
                    </div>
                  </SmartLink>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
