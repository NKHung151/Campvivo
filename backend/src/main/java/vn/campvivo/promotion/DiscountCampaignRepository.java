package vn.campvivo.promotion;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DiscountCampaignRepository extends JpaRepository<DiscountCampaign, Long>,
        JpaSpecificationExecutor<DiscountCampaign> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @EntityGraph(attributePaths = "products")
    Optional<DiscountCampaign> findWithProductsById(Long id);

    /** Chiến dịch đang hiệu lực theo slug (trang /promotions/[slug]) — cùng điều kiện "hiệu lực" với Product.salePrice. */
    @Query(value = """
            select * from discount_campaigns
            where slug = :slug and is_active and start_date <= now() and end_date > now()
            """, nativeQuery = true)
    Optional<DiscountCampaign> findActiveBySlug(@Param("slug") String slug);

    /** Chiến dịch đang hiệu lực, sắp kết thúc trước (GET /campaigns). */
    @Query(value = """
            select * from discount_campaigns
            where is_active and start_date <= now() and end_date > now()
            order by end_date, id
            """, nativeQuery = true)
    List<DiscountCampaign> findAllActive();

    /** Số sản phẩm ĐANG BÁN của từng chiến dịch: [campaignId, count]. */
    @Query("select c.id, count(p) from DiscountCampaign c join c.products p where p.active = true and c.id in :ids group by c.id")
    List<Object[]> countActiveProducts(@Param("ids") Collection<Long> ids);

    /**
     * Chiến dịch đang áp cho từng sản phẩm (mức giảm tốt nhất, hoà thì kết thúc sớm hơn): [productId, name, slug, endDate].
     * Công thức giá khớp Product.salePrice (D-30).
     */
    @Query(value = """
            select distinct on (cp.product_id) cp.product_id, c.name, c.slug, c.end_date
            from discount_campaign_products cp
                join discount_campaigns c on c.id = cp.campaign_id
                join products p on p.id = cp.product_id
            where cp.product_id in (:productIds) and c.is_active and c.start_date <= now() and c.end_date > now()
            order by cp.product_id,
                     case when c.discount_type = 'PERCENTAGE' then round(p.price * (100 - c.discount_value) / 100.0)
                          else greatest(p.price - c.discount_value, 0) end,
                     c.end_date
            """, nativeQuery = true)
    List<Object[]> findBestActiveForProducts(@Param("productIds") Collection<Long> productIds);

    /**
     * Sản phẩm đã thuộc chiến dịch khác ĐANG BẬT có thời gian giao [start, end) — chỉ để cảnh báo admin (BR-6 vẫn cho chồng).
     * `excludeId` = -1 khi tạo mới. Kết quả: [productId, campaignId, campaignName].
     */
    @Query(value = """
            select cp.product_id, c.id, c.name
            from discount_campaign_products cp join discount_campaigns c on c.id = cp.campaign_id
            where cp.product_id in (:productIds) and c.id <> :excludeId and c.is_active
              and c.start_date < :endDate and c.end_date > :startDate
            order by cp.product_id, c.start_date
            """, nativeQuery = true)
    List<Object[]> findOverlaps(@Param("productIds") Collection<Long> productIds,
                                @Param("startDate") OffsetDateTime startDate,
                                @Param("endDate") OffsetDateTime endDate,
                                @Param("excludeId") long excludeId);

    /** Số sản phẩm của từng chiến dịch: [campaignId, count]. */
    @Query("select c.id, count(p) from DiscountCampaign c left join c.products p where c.id in :ids group by c.id")
    List<Object[]> countProducts(@Param("ids") Collection<Long> ids);
}
