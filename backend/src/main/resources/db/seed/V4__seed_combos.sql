-- Dữ liệu mẫu combo cho môi trường dev (chỉ chạy khi spring.flyway.locations có classpath:db/seed).
-- 5 combo ghép từ sản phẩm có sẵn trong seed V3 (tham chiếu theo slug), mỗi combo gắn danh mục cấp 1 (D-23).
-- Bốn combo được "đóng gói" sẵn: trừ kho sản phẩm thành phần đúng như thao tác Đóng gói trong admin (D-22);
-- combo cuối để 0 bộ.

-- Combo không lưu ảnh riêng: ảnh hiển thị lấy từ ảnh chính của các sản phẩm thành phần.
CREATE TEMP TABLE seed_combo (
    slug        VARCHAR(280) PRIMARY KEY,
    name        VARCHAR(255),
    root_slug   VARCHAR(180),
    price       NUMERIC(19, 0),
    description TEXT,
    sets        INT
) ON COMMIT DROP;

INSERT INTO seed_combo VALUES
    ('combo-cam-trai-1-nguoi-co-ban', 'Combo cắm trại 1 người cơ bản', 'do-cam-trai', 5490000,
     '<p><strong>Đủ đồ cho một đêm cắm trại một mình:</strong> lều dựng nhanh, túi ngủ, ghế gấp siêu nhẹ và dây đèn LED trang trí.</p><ul><li>Lều Coleman Instant Up 1 người</li><li>Túi ngủ nỉ Northpeak</li><li>Ghế gấp Snowline Lasse (Mid Green)</li><li>Dây đèn LED Naturehike</li></ul>', 5),
    ('combo-bep-da-ngoai-gon-nhe', 'Combo bếp dã ngoại gọn nhẹ', 'do-cam-trai', 1290000,
     '<p>Bếp gas siêu nhẹ kèm 3 bình gas và balo giữ lạnh 10L — nấu ăn, giữ đồ uống mát cho cả chuyến đi.</p>', 8),
    ('combo-leo-nui-trong-ngay', 'Combo leo núi trong ngày', 'do-leo-nui', 1390000,
     '<p>Gọn nhẹ cho các cung trekking trong ngày: balo 20L, đôi gậy chống sốc, đèn cài mũ và áo mưa trùm balo.</p>', 6),
    ('combo-boi-cho-be', 'Combo bơi cho bé', 'boi-lan', 299000,
     '<p>Bộ đồ bơi cho bé: kính bơi chống sương, phao vòng 91cm và mũ bơi Intex.</p>', 10),
    ('combo-ngu-am-mua-dong', 'Combo ngủ ấm mùa đông', 'do-leo-nui', 1590000,
     '<p>Túi ngủ mùa đông 2 lớp kèm đèn LED Coleman Micropacker cho những đêm lạnh trên núi.</p>', 0);

-- Thành phần: (combo, sản phẩm, số lượng/bộ)
CREATE TEMP TABLE seed_combo_item (
    combo_slug   VARCHAR(280),
    product_slug VARCHAR(280),
    quantity     INT,
    position     INT
) ON COMMIT DROP;

INSERT INTO seed_combo_item VALUES
    ('combo-cam-trai-1-nguoi-co-ban', 'leu-1-nguoi-coleman-instant-up-dome-s', 1, 1),
    ('combo-cam-trai-1-nguoi-co-ban', 'tui-ngu-ni-northpeak-sleeping-bag-7855', 1, 2),
    ('combo-cam-trai-1-nguoi-co-ban', 'ghe-gap-sieu-nhe-lung-tua-thap-snowline-lasse-light-chair-snd5ulc002', 1, 3),
    ('combo-cam-trai-1-nguoi-co-ban', 'den-day-led-trang-tri-leu-trai-naturehike-led-string-lights-cnh22dq019', 1, 4),
    ('combo-bep-da-ngoai-gon-nhe', 'bep-gas-da-ngoai-naturehike-ultra-light-gas-stove-nh21rj004', 1, 1),
    ('combo-bep-da-ngoai-gon-nhe', 'binh-gas-da-ngoai-maxsun-max-camping', 3, 2),
    ('combo-bep-da-ngoai-gon-nhe', 'balo-giu-lanh-10l-coleman-cool-pack-blue-2000024609-7402', 1, 3),
    ('combo-leo-nui-trong-ngay', 'balo-leo-nui-20l-senterlan-volume-s2321-9213-den', 1, 1),
    ('combo-leo-nui-trong-ngay', 'gay-leo-nui-chong-soc-an-anti-shock-cleye', 2, 2),
    ('combo-leo-nui-trong-ngay', 'den-cai-mu-luoi-trai-5-led-ryder-cap-torch-k0008-6723', 1, 3),
    ('combo-leo-nui-trong-ngay', 'ao-mua-balo-30-50l-ryder-rucksack-raincover-m-f1002-6699', 1, 4),
    ('combo-boi-cho-be', 'kinh-boi-tre-em-cao-cap-intex-55693-9124', 1, 1),
    ('combo-boi-cho-be', 'phao-boi-vong-91cm-intex-59251', 1, 2),
    ('combo-boi-cho-be', 'mu-boi-intex-55991', 1, 3),
    ('combo-ngu-am-mua-dong', 'tui-ngu-mua-dong-ryder-d1005', 1, 1),
    ('combo-ngu-am-mua-dong', 'den-led-coleman-micropacker-5319-700', 1, 2);

INSERT INTO product_combos (category_id, name, slug, description, combo_price, stock_quantity, reserved_quantity,
                            is_active, created_at, updated_at)
SELECT c.id, s.name, s.slug, s.description, s.price, 0, 0, TRUE, now(), now()
FROM seed_combo s
JOIN categories c ON c.slug = s.root_slug AND c.parent_id IS NULL;

INSERT INTO combo_items (combo_id, product_id, quantity)
SELECT pc.id, p.id, i.quantity
FROM seed_combo_item i
JOIN product_combos pc ON pc.slug = i.combo_slug
JOIN products p ON p.slug = i.product_slug
ORDER BY i.combo_slug, i.position;

-- Đóng gói sẵn: trừ kho sản phẩm thành phần (số lượng × số bộ), cộng số bộ vào kho combo (D-22).
UPDATE products p
SET stock_quantity = p.stock_quantity - ci.quantity * s.sets,
    updated_at     = now()
FROM combo_items ci
JOIN product_combos pc ON pc.id = ci.combo_id
JOIN seed_combo s ON s.slug = pc.slug
WHERE ci.product_id = p.id AND s.sets > 0;

UPDATE product_combos pc
SET stock_quantity = s.sets
FROM seed_combo s
WHERE s.slug = pc.slug;
