package vn.campvivo.promotion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.campvivo.common.config.AppProperties;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Công bố voucher công khai / chiến dịch vừa tới lúc hiệu lực (D-45), mỗi `app.notification.announce-job-delay` (60s).
 * Mỗi lô một transaction: khoá `FOR UPDATE SKIP LOCKED` các dòng `announced_at IS NULL` đủ điều kiện, gán `announced_at`,
 * phát {@link VoucherAnnouncedEvent} / {@link CampaignStartedEvent} — listener nhận SAU commit nên mỗi mã / chiến dịch được
 * công bố đúng một lần kể cả khi hai lần chạy chồng nhau. Mã hẹn giờ và mã hiệu lực ngay đi chung một đường này; sửa sau khi
 * đã công bố không gửi lại.
 */
@Component
public class PromotionAnnouncementJob implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(PromotionAnnouncementJob.class);
    static final int BATCH = 50;

    private final VoucherRepository vouchers;
    private final DiscountCampaignRepository campaigns;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final AppProperties.Notification props;
    private final Clock clock;

    public PromotionAnnouncementJob(VoucherRepository vouchers, DiscountCampaignRepository campaigns,
                                    ApplicationEventPublisher events, PlatformTransactionManager transactionManager,
                                    AppProperties appProperties, Clock clock) {
        this.vouchers = vouchers;
        this.campaigns = campaigns;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
        this.props = appProperties.notification();
        this.clock = clock;
    }

    /** Lần đầu chạy sau một chu kỳ (không chạy ngay lúc khởi động). */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(this::run, props.announceJobDelay(), props.announceJobDelay()));
    }

    /** Chạy theo lịch; lỗi chỉ ghi log để lần sau chạy lại (dòng chưa được gán `announced_at`). */
    void run() {
        try {
            announceDue();
        } catch (RuntimeException e) {
            log.error("Job công bố khuyến mãi lỗi", e);
        }
    }

    /** Công bố mọi voucher / chiến dịch đang tới hạn; trả tổng số đã công bố. */
    public int announceDue() {
        int total = 0;
        int n;
        do {
            n = announceVouchers();
            total += n;
        } while (n == BATCH);
        do {
            n = announceCampaigns();
            total += n;
        } while (n == BATCH);
        if (total > 0) {
            log.info("Đã công bố {} voucher / chiến dịch", total);
        }
        return total;
    }

    private int announceVouchers() {
        Integer n = tx.execute(s -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            List<Voucher> due = vouchers.lockDueForAnnouncement(now, BATCH);
            due.forEach(v -> {
                v.markAnnounced(now);
                events.publishEvent(VoucherAnnouncedEvent.of(v));
            });
            return due.size();
        });
        return n == null ? 0 : n;
    }

    private int announceCampaigns() {
        Integer n = tx.execute(s -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            List<DiscountCampaign> due = campaigns.lockDueForAnnouncement(now, BATCH);
            due.forEach(c -> {
                c.markAnnounced(now);
                events.publishEvent(new CampaignStartedEvent(c.getId(), c.getName(), c.getSlug(),
                        campaigns.findProductIds(c.getId())));
            });
            return due.size();
        });
        return n == null ? 0 : n;
    }
}
