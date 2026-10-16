---
name: testing-qa
description: Viết và chạy test cho các luồng nghiệp vụ quan trọng của Campvivo, đặc biệt
  chống bán vượt tồn kho và idempotency thanh toán.
---

# Testing & QA

## Khi nào dùng
Sau khi code xong 1 luồng nghiệp vụ, hoặc khi được yêu cầu viết test riêng.

## Quy trình
1. Theo `.agents/rules/testing.md`.
2. Với luồng đặt hàng/tồn kho: viết test tích hợp dùng Testcontainers (PostgreSQL thật),
   không mock DB.
3. Test race condition: N request đặt hàng song song cùng 1 sản phẩm (và cùng 1 combo) còn ít tồn
   kho, assert tổng số lượng bán ra không vượt `stock_quantity` ban đầu; voucher còn 1 lượt → đúng 1
   đơn dùng được. Dùng `ExecutorService` + `CountDownLatch` để các request bắt đầu cùng lúc.
4. Test IPN VNPay: gọi 2 lần cùng giao dịch → xử lý 1 lần; sai chữ ký / sai số tiền → từ chối.
5. Test máy trạng thái đơn + trả hàng (cộng tồn kho, đơn RETURNED), job huỷ đơn quá hạn.
6. Test biên ở frontend: giỏ hàng rỗng, voucher hết hạn, sản phẩm hết hàng giữa chừng,
   `PRICE_CHANGED`, thanh toán thất bại.
7. Danh sách đầy đủ test bắt buộc: `.agents/rules/testing.md`.

## Tiêu chí hoàn thành
- Test pass, gồm test race condition cho đặt hàng (sản phẩm + combo) và voucher.
- Không giảm coverage của luồng Tier 1 cốt lõi.
