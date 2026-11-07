-- V7 (seed, chỉ profile dev): voucher + chiến dịch mẫu — database.md mục 7. Thời gian tính theo lúc chạy seed.
-- Sản phẩm của chiến dịch lấy từ seed V3 (8 sản phẩm đang bán có giá từ 300.000đ, id nhỏ nhất) để không phụ thuộc id cố định.

INSERT INTO vouchers (code, discount_type, discount_value, max_discount, min_order_value, quantity, start_date,
                      expiry_date, is_active, is_public) VALUES
    ('CAMPVIVO10', 'PERCENTAGE',   10,    50000,  500000, 100, now() - interval '1 day', now() + interval '60 days', TRUE, TRUE),
    ('GIAM50K',    'FIXED_AMOUNT', 50000, NULL,  1500000,  50, now() - interval '1 day', now() + interval '30 days', TRUE, TRUE),
    ('WELCOME50K', 'FIXED_AMOUNT', 50000, NULL,   300000, 500, now() - interval '1 day', now() + interval '365 days', TRUE, FALSE);

INSERT INTO discount_campaigns (name, slug, description, discount_type, discount_value, start_date, end_date, is_active) VALUES
    ('Xả hàng cuối mùa', 'xa-hang-cuoi-mua',
     '<p>Giảm 20% cho các sản phẩm dã ngoại được chọn. Áp dụng đến hết thời gian chiến dịch, không áp cho combo.</p>',
     'PERCENTAGE', 20, now() - interval '1 day', now() + interval '30 days', TRUE),
    ('Chào hè cùng Campvivo', 'chao-he-cung-campvivo',
     '<p>Giảm ngay 100.000đ cho các sản phẩm chọn lọc mùa hè.</p>',
     'FIXED_AMOUNT', 100000, now() + interval '7 days', now() + interval '37 days', TRUE);

INSERT INTO discount_campaign_products (campaign_id, product_id)
SELECT c.id, p.id
FROM discount_campaigns c,
     (SELECT id FROM products WHERE is_active AND price >= 300000 ORDER BY id LIMIT 8) p
WHERE c.slug = 'xa-hang-cuoi-mua';

INSERT INTO discount_campaign_products (campaign_id, product_id)
SELECT c.id, p.id
FROM discount_campaigns c,
     (SELECT id FROM products WHERE is_active AND price >= 500000 ORDER BY id DESC LIMIT 6) p
WHERE c.slug = 'chao-he-cung-campvivo';
