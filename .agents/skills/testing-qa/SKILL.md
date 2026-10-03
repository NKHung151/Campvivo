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
3. Test race condition: giả lập N request đặt hàng song song cùng 1 sản phẩm còn ít tồn kho,
   assert tổng số lượng bán ra không vượt `stock_quantity` ban đầu.
4. Test webhook thanh toán: gọi webhook 2 lần với cùng `transaction_id`, assert chỉ xử lý
   1 lần.
5. Test biên ở frontend: giỏ hàng rỗng, mã giảm giá hết hạn, sản phẩm hết hàng giữa chừng.

## Tiêu chí hoàn thành
- Test pass, bao gồm ít nhất 1 test race condition cho luồng đặt hàng.
- Không giảm coverage của luồng Tier 1 cốt lõi.
