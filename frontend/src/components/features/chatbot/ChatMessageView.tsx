import Link from "next/link";
import { comboHref, productHref, vnd } from "@/lib/format";
import type { ChatCard, ChatRole, ChatSource } from "@/types/api/chatbot";
import { formatMessage } from "./formatMessage";

/** Một tin nhắn trong khung chat: chữ + thẻ sản phẩm/combo + nguồn tài liệu (dữ liệu thẻ lấy từ DB, không từ chữ). */
export function ChatMessageView({
  role,
  content,
  cards = [],
  sources = [],
  streaming = false,
}: {
  role: ChatRole;
  content: string;
  cards?: ChatCard[];
  sources?: ChatSource[];
  streaming?: boolean;
}) {
  if (role === "USER") {
    return (
      <div className="cv-chat-msg is-user">
        <div className="cv-chat-bubble">{content}</div>
      </div>
    );
  }
  return (
    <div className="cv-chat-msg is-bot">
      <div className="cv-chat-bubble" aria-live={streaming ? "polite" : undefined}>
        {content ? formatMessage(content) : <TypingDots />}
        {streaming && content && <span className="cv-chat-caret" aria-hidden />}
      </div>
      {cards.length > 0 && (
        <div className="cv-chat-cards">
          {cards.map((c) => (
            <CardView key={`${c.type}-${c.id}`} card={c} />
          ))}
        </div>
      )}
      {sources.length > 0 && (
        <div className="cv-chat-sources">
          <span>Nguồn:</span>
          {sources.map((s) => (
            <span key={`${s.documentId}-${s.page}`} className="cv-chat-source">
              {s.title}
              {s.page !== null ? `, tr. ${s.page}` : ""}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function CardView({ card }: { card: ChatCard }) {
  const href = card.type === "COMBO" ? comboHref(card.slug) : productHref(card.slug);
  return (
    <Link href={href} className="cv-chat-card">
      {card.primaryImageUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={card.primaryImageUrl} alt="" loading="lazy" />
      ) : (
        <span className="cv-chat-card-noimg" aria-hidden />
      )}
      <span className="cv-chat-card-body">
        {card.type === "COMBO" && <span className="cv-chat-card-badge">Combo</span>}
        <span className="cv-chat-card-name">{card.name}</span>
        <span className="cv-chat-card-price">
          {vnd(card.price, "đ")}
          {card.suggestedQuantity !== null && card.suggestedQuantity > 1 && <span> × {card.suggestedQuantity}</span>}
        </span>
        {card.note && <span className="cv-chat-card-note">{card.note}</span>}
        {card.availableQuantity <= 0 && <span className="cv-chat-card-out">Tạm hết hàng</span>}
      </span>
    </Link>
  );
}

function TypingDots() {
  return (
    <span className="cv-chat-typing" aria-label="Trợ lý đang trả lời">
      <i />
      <i />
      <i />
    </span>
  );
}
