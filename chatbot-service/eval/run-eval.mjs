// Chạy bộ câu hỏi đánh giá chatbot (CB-27) trên hệ thống đang chạy thật (backend :8080, chatbot :8090, có OPENAI_API_KEY).
// Cách chạy (Node 18+, không cần cài gói):
//   node chatbot-service/eval/run-eval.mjs [email] [password]
// Mặc định dùng tài khoản mẫu customer@campvivo.local / Customer@123 (docs/04-van-hanh/local-development.md).
// Kết quả: bảng tóm tắt ra màn hình + file chatbot-service/eval/report-<ngày>.md (đưa vào báo cáo).
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const DIR = path.dirname(fileURLToPath(import.meta.url));
const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080/api";
const CHATBOT = process.env.CHATBOT_URL ?? "http://localhost:8090/api";
const [email = "customer@campvivo.local", password = "Customer@123"] = process.argv.slice(2);

const fold = (s) => s.toLowerCase().normalize("NFD").replace(/\p{M}/gu, "").replace(/đ/g, "d");
const money = (s) => [...s.matchAll(/(\d{1,3}(?:\.\d{3})+)\s*(?:đ|₫|vnd)/gi)].map((m) => Number(m[1].replace(/\./g, "")));

async function login() {
  const res = await fetch(`${BACKEND}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!body.success) throw new Error("Đăng nhập lỗi: " + body.message);
  return { Authorization: "Bearer " + body.data.accessToken };
}

async function ask(auth, question) {
  const conv = (await (await fetch(`${CHATBOT}/chatbot/conversations`, { method: "POST", headers: auth })).json()).data;
  const started = Date.now();
  const res = await fetch(`${CHATBOT}/chatbot/conversations/${conv.id}/messages`, {
    method: "POST",
    headers: { ...auth, "Content-Type": "application/json", Accept: "text/event-stream, application/json" },
    body: JSON.stringify({ content: question }),
  });
  const text = await res.text();
  const ms = Date.now() - started;
  const events = text.split(/\n\n/).filter(Boolean).map((b) => ({
    event: /event:\s*(\S+)/.exec(b)?.[1],
    data: b.split("\n").filter((l) => l.startsWith("data:")).map((l) => l.slice(5)).join("\n"),
  }));
  const get = (name) => events.find((e) => e.event === name);
  return {
    ms,
    answer: events.filter((e) => e.event === "delta").map((e) => JSON.parse(e.data).text).join(""),
    cards: JSON.parse(get("cards")?.data ?? "[]"),
    sources: JSON.parse(get("sources")?.data ?? "[]"),
    done: get("done") ? JSON.parse(get("done").data) : null,
    error: get("error") ? JSON.parse(get("error").data) : res.ok ? null : { message: text.slice(0, 200) },
  };
}

function judge(item, r) {
  const problems = [];
  if (r.error) return [`lỗi: ${r.error.errorCode ?? ""} ${r.error.message ?? ""}`];
  const hasCards = r.cards.length > 0;
  switch (item.expect) {
    case "TRIP":
      if (!hasCards) problems.push("không có thẻ đề xuất");
      if (item.comboSlug && !r.cards.some((c) => c.type === "COMBO" && c.slug === item.comboSlug)) problems.push(`thiếu combo ${item.comboSlug}`);
      break;
    case "SEARCH":
      if (!hasCards) problems.push("không có thẻ sản phẩm/combo");
      break;
    case "KNOWLEDGE":
      if (r.sources.length === 0) problems.push("không trích nguồn tài liệu");
      break;
    case "CLARIFY":
      if (hasCards) problems.push("đáng lẽ hỏi lại nhưng đã đề xuất sản phẩm");
      if (!r.answer.includes("?")) problems.push("không thấy câu hỏi lại");
      break;
    case "REFUSE":
      if (hasCards || r.sources.length > 0) problems.push("đáng lẽ từ chối");
      if (/campvivo|bạn là|system prompt|hướng dẫn/i.test(r.answer) && /##|CHỈ lấy|tool/i.test(r.answer)) problems.push("có dấu hiệu lộ prompt");
      break;
    default:
      break;
  }
  for (const phrase of item.mustInclude ?? []) {
    if (!fold(r.answer).includes(fold(phrase))) problems.push(`thiếu "${phrase}"`);
  }
  return problems;
}

/** Số tiền trong câu trả lời không khớp giá thẻ (giá, giá × số lượng) — để người duyệt xem lại, không tính là sai. */
function unmatchedMoney(item, r) {
  const known = new Set();
  for (const c of r.cards) {
    known.add(c.price);
    if (c.suggestedQuantity) known.add(c.price * c.suggestedQuantity);
  }
  for (const n of money(item.q)) known.add(n);
  return money(r.answer).filter((n) => !known.has(n));
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
/** Giới hạn app.chatbot.rate-limit.per-minute (20): giãn nhịp, gặp 429 thì chờ đúng retryAfterSeconds rồi hỏi lại. */
const PACE_MS = 3200;

async function askPaced(auth, question) {
  for (let attempt = 0; attempt < 3; attempt++) {
    const r = await ask(auth, question);
    const limited = /CHAT_RATE_LIMITED/.test(r.error?.message ?? "");
    if (!limited) return r;
    const wait = Number(/"retryAfterSeconds":(\d+)/.exec(r.error.message)?.[1] ?? 60);
    console.log(`  … chờ ${wait}s vì giới hạn tần suất`);
    await sleep((wait + 1) * 1000);
  }
  return ask(auth, question);
}

const { questions } = JSON.parse(fs.readFileSync(path.join(DIR, "questions.json"), "utf8"));
const auth = await login();
const rows = [];
for (const [i, item] of questions.entries()) {
  const r = await askPaced(auth, item.q);
  await sleep(PACE_MS);
  const problems = judge(item, r);
  const extraMoney = unmatchedMoney(item, r);
  rows.push({ i: i + 1, item, r, problems, extraMoney });
  console.log(`${problems.length ? "✗" : "✓"} ${String(i + 1).padStart(2)} [${item.expect}] ${item.q} (${r.ms} ms)${problems.length ? " — " + problems.join("; ") : ""}`);
}

const passed = rows.filter((x) => x.problems.length === 0).length;
const byType = {};
for (const x of rows) {
  byType[x.item.expect] ??= { total: 0, ok: 0 };
  byType[x.item.expect].total++;
  if (!x.problems.length) byType[x.item.expect].ok++;
}
const avg = (k) => Math.round(rows.reduce((s, x) => s + (x.r.done?.[k] ?? 0), 0) / rows.length);
const avgMs = Math.round(rows.reduce((s, x) => s + x.r.ms, 0) / rows.length);

const date = new Date().toISOString().slice(0, 10);
const md = [
  `# Đánh giá chatbot — ${date}`,
  "",
  `Đạt **${passed}/${rows.length}** câu. Thời gian trả lời trung bình ${avgMs} ms; token trung bình: vào ${avg("promptTokens")}, ra ${avg("completionTokens")}.`,
  "",
  "| Nhóm | Đạt |",
  "|---|---|",
  ...Object.entries(byType).map(([k, v]) => `| ${k} | ${v.ok}/${v.total} |`),
  "",
  "| # | Nhóm | Câu hỏi | Kết quả | Thẻ | Nguồn | Số tiền cần xem lại | ms |",
  "|---|---|---|---|---|---|---|---|",
  ...rows.map((x) => `| ${x.i} | ${x.item.expect} | ${x.item.q} | ${x.problems.length ? "✗ " + x.problems.join("; ") : "✓"} | ${x.r.cards.length} | ${x.r.sources.map((s) => s.title).join(", ")} | ${x.extraMoney.join(", ")} | ${x.r.ms} |`),
  "",
  "## Câu trả lời",
  ...rows.flatMap((x) => ["", `### ${x.i}. ${x.item.q}`, "", x.r.answer || `_(lỗi: ${x.r.error?.message ?? ""})_`]),
].join("\n");
const out = path.join(DIR, `report-${date}.md`);
fs.writeFileSync(out, md);
console.log(`\nĐạt ${passed}/${rows.length}. Báo cáo: ${out}`);
