# Truy vết Use case → FR → API → Route → Bảng

Ánh xạ từng use case trong báo cáo Phase1_v4 (bảng 2.3–2.11, giữ nguyên tên tiếng Anh) sang phần
hiện thực. Khi code một use case: tra dòng tương ứng, đọc FR (`features.md`), API (`api.md`), quy tắc
(BR-xx trong `database.md`). Use case không có trong báo cáo được đánh dấu **(bổ sung)** kèm mã
quyết định.

## Xác thực và thông tin người dùng (bảng 2.3, 2.8)
| Use case | Actor | FR | API | Route | Bảng |
|---|---|---|---|---|---|
| Authenticate User | Guest | FR-01/02 | `/auth/*` | `/login`, `/register` | users |
| Register | Guest | FR-01 | `POST /auth/register` | `/register` | users, carts |
| Type individual information | Guest | FR-01 | (body của register) | `/register` | users |
| Authenticate email | Guest | FR-01 | `POST /auth/verify-email`, `POST /auth/resend-code` | `/verify-email` | users |
| Login | Guest | FR-02 | `POST /auth/login` | `/login` | users |
| Reset Password | Guest | FR-03 | `POST /auth/forgot-password` | `/forgot-password` | users |
| Type new password | Guest | FR-03 | `POST /auth/reset-password` | `/reset-password` | users |
| Logout | User | FR-02 | `POST /auth/logout` | (header) | users.token_version |
| View individual profile | User | FR-04 | `GET /users/me` | `/account` | users |
| Update Profile | User | FR-04 | `PUT /users/me` | `/account` | users |
| Change Password | User | FR-04 | `PUT /users/me/password` | `/account` | users |

## Quản trị hệ thống (bảng 2.4, 2.9)
| Use case | Actor | FR | API | Route | Bảng |
|---|---|---|---|---|---|
| Manage system | Admin | — | `/admin/**` | `/admin` | — |
| View analytics dashboard | Admin | FR-24 | `GET /admin/analytics/summary`, `/revenue`, `/top-items` | `/admin` | orders, order_items |
| Filter by date range / month / year | Admin | FR-24 | `?from&to&groupBy=day\|month\|year` | `/admin` | orders |
| Manage users / View user list / Search users | Admin | FR-14 | `GET /admin/users?q=` | `/admin/users` | users |
| View user detail | Admin | FR-14 | `GET /admin/users/{id}` | `/admin/users` | users, orders |
| Update user | Admin | FR-14 | `PATCH /admin/users/{id}` | `/admin/users` | users |
| Manage orders / View all orders / Filter orders by status | Admin, Employee | FR-13 | `GET /admin/orders?status=` | `/admin/orders` | orders |
| View order detail | Admin, Employee | FR-13 | `GET /admin/orders/{id}` | `/admin/orders/[id]` | orders, order_items, order_status_history, payments |
| Update order status | Admin, Employee | FR-13 | `PATCH /admin/orders/{id}/status` | `/admin/orders/[id]` | orders, order_status_history, products, product_combos, payments |
| Process return request **(bổ sung, D-07)** | Admin, Employee | FR-19 | `GET /admin/returns`, `PATCH /admin/returns/{id}` | `/admin/returns` | return_requests, orders, products, product_combos, inventory_logs |
| Manage Inventory / View stock levels | Admin, Employee | FR-08 | `GET /admin/inventory` | `/admin/inventory` | products, product_combos |
| Adjust stock | Admin, Employee | FR-08 | `POST /admin/inventory/adjustments`, `GET /admin/inventory/logs` | `/admin/inventory` | products, product_combos, inventory_logs |
| Manage products / View product list / detail | Admin | FR-06 | `GET /admin/products[/{id}]` | `/admin/products` | products, images |
| Create product / Update product | Admin | FR-06 | `POST/PUT /admin/products`, `POST /admin/uploads/images` | `/admin/products/new`, `/[id]` | products, images |
| Manage categories / View categories list | Admin | FR-05 | `GET /admin/categories` | `/admin/categories` | categories |
| Create / Update / Delete category | Admin | FR-05 | `POST/PUT/DELETE /admin/categories` | `/admin/categories` | categories |
| Manage combos / View combo list / detail | Admin | FR-15 | `GET /admin/combos[/{id}]` | `/admin/combos` | product_combos, combo_items, images |
| Create combo / Update combo | Admin | FR-15 | `POST/PUT /admin/combos` | `/admin/combos/new`, `/[id]` | product_combos, combo_items, images |
| Manage promotions | Admin | FR-16/17 | `/admin/vouchers`, `/admin/campaigns` | `/admin/vouchers`, `/admin/campaigns` | vouchers, discount_campaigns |
| View voucher list / Add / Update / Delete voucher | Admin | FR-16 | `GET/POST/PUT/DELETE /admin/vouchers` | `/admin/vouchers` | vouchers |
| View discount campaigns / Add campaign / Update campaign | Admin | FR-17 | `GET/POST/PUT /admin/campaigns` | `/admin/campaigns` | discount_campaigns, discount_campaign_products |
| Manage article | Admin | FR-21 | `GET/POST/PUT/DELETE /admin/articles` | `/admin/articles` | articles |

## Mua hàng (bảng 2.5, 2.10)
| Use case | Actor | FR | API | Route | Bảng |
|---|---|---|---|---|---|
| Manage cart | Customer | FR-09 | `GET /cart` | `/cart` | carts, cart_items |
| Add to cart / Select a item | Customer | FR-09 | `POST /cart/items` | `/products/[slug]`, `/combos/[slug]` | cart_items |
| Update cart item | Customer | FR-09 | `PATCH /cart/items/{id}` | `/cart` | cart_items |
| Remove cart item | Customer | FR-09 | `DELETE /cart/items/{id}` | `/cart` | cart_items |
| Apply promotion code | Customer | FR-10/16 | `POST /vouchers/validate`, `POST /orders/preview` | `/cart` | vouchers |
| Place order | Customer | FR-10 | `POST /orders` | `/cart` | orders, order_items, order_status_history, payments, products, product_combos, vouchers, cart_items |
| Select shipping address | Customer | FR-10 | (body của `POST /orders`) | `/cart` | orders, users |
| Choose payment method | Customer | FR-10 | (body: `VNPAY` \| `COD`) | `/cart` | orders, payments |
| Pay order | Customer, System | FR-11 | `paymentUrl`, `GET /payments/vnpay/ipn`, `/return` | `/checkout/success` | payments, orders |
| Manage individual orders / View orders history | Customer | FR-12 | `GET /orders` | `/account/orders` | orders |
| View order detail | Customer | FR-12 | `GET /orders/{id}` | `/account/orders/[id]` | orders, order_items, order_status_history, payments, return_requests |
| Cancel order | Customer | FR-12 | `POST /orders/{id}/cancel` | `/account/orders/[id]` | orders, products, product_combos, vouchers |
| Return order | Customer | FR-19 | `POST /orders/{id}/returns` | `/account/orders/[id]` | return_requests |
| Write review | Customer | FR-18 | `POST /reviews` | `/account/orders/[id]` | reviews |
| Update review | Customer | FR-18 | `PUT /reviews/{id}` | `/account/reviews` | reviews |
| Manage review (+ xoá) | Customer | FR-18 | `GET /users/me/reviews`, `DELETE /reviews/{id}` | `/account/reviews` | reviews |
| Manage orders / Update order status | Employee, Admin | FR-13 | (xem phân hệ quản trị) | `/admin/orders` | — |

## Khám phá và hỗ trợ (bảng 2.6, 2.11)
| Use case | Actor | FR | API | Route | Bảng |
|---|---|---|---|---|---|
| View homepage | Guest+ | FR-07 | `/products`, `/combos`, `/articles` | `/` | products, product_combos, articles |
| Search | Guest+ | FR-07 | `GET /search?q=` | `/search` | products, product_combos, articles |
| View result products / combos / articles | Guest+ | FR-07 | `GET /search?type=` | `/search` | — |
| View products | Guest+ | FR-07 | `GET /products` | `/products`, `/categories/[slug]`, `/brands/[slug]` | products |
| View product detail | Guest+ | FR-07 | `GET /products/{slug}` (+ reviews, recommendations) | `/products/[slug]` | products, images, reviews |
| Filter by category | Guest+ | FR-07 | `?category=` | `/categories/[slug]` | categories |
| Sort | Guest+ | FR-07 | `?sortBy=` | danh sách | — |
| View combos | Guest+ | FR-07/15 | `GET /combos` | `/combos` | product_combos |
| View combo detail | Guest+ | FR-07/15 | `GET /combos/{slug}` | `/combos/[slug]` | product_combos, combo_items, images |
| View articles | Guest+ | FR-21 | `GET /articles?category=` | `/blog`, `/journal`, `/guides` | articles |
| View article detail | Guest+ | FR-21 | `GET /articles/{slug}` | `/blog/[slug]`, `/journal/[slug]` | articles |
| Manage favorites / View individual favorite | Customer | FR-20 | `GET /users/me/favorites` | `/account/favorites` | favorites |
| Add to favorite | Customer | FR-20 | `POST /favorites` | trang chi tiết | favorites |
| Remove favorite | Customer | FR-20 | `DELETE /favorites/{id}` | `/account/favorites` | favorites |
| Exchange support messages | Customer, Employee, Admin | FR-26 | `/support/messages`, `/admin/support/*`, WS | `/account/support`, `/admin/support` | support_messages |

## Hệ thống (bảng 2.7)
| Use case | Actor | FR | Hiện thực | Bảng |
|---|---|---|---|---|
| Push realtime notification | System | FR-22 | Event listener AFTER_COMMIT → WS `/user/queue/notifications` | notifications |
| Send email | System | FR-23 | `notification` module, Thymeleaf template, `@Async` | — |
| Chat | System | FR-26 | WS `/user/queue/support` (chat với nhân viên; chatbot AI là FR-28, chưa làm — D-05) | support_messages |
| View notification | User | FR-22 | `GET /notifications`, route `/account/notifications` | notifications |
| Mark as read all | User | FR-22 | `PATCH /notifications/read-all` | notifications |
| Filter by status | User | FR-22 | `GET /notifications?isRead=` | notifications |
| Giữ chỗ / giải phóng tồn kho | System | FR-10/11 | `InventoryService`, `OrderTimeoutJob` (BR-2, BR-3) | products, product_combos |
| Gợi ý combo, sản phẩm | System | FR-25 | `GET /products/{id}/recommendations`, `/combos/{id}/recommendations` | order_items, orders |
