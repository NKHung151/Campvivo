package vn.campvivo.chatbot.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import vn.campvivo.chatbot.catalog.CatalogReadPort;
import vn.campvivo.chatbot.common.config.ChatbotProperties;
import vn.campvivo.chatbot.embedding.ItemEmbeddingRepository.ItemType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Giữ `chatbot_item_embeddings` khớp catalog: chạy khi app sẵn sàng rồi lặp mỗi
 * `app.chatbot.embedding.refresh-interval`. Chỉ embed mặt hàng có văn bản đổi (so `content_hash`), xoá dòng của
 * mặt hàng đã ngừng bán. Lỗi gọi OpenAI (chưa có key, hết hạn mức) chỉ ghi log — tìm kiếm vẫn chạy bằng chữ.
 */
@Component
public class ItemEmbeddingJob {

    private static final Logger log = LoggerFactory.getLogger(ItemEmbeddingJob.class);
    private static final int BATCH = 100;

    private final CatalogReadPort catalog;
    private final ItemEmbeddingRepository repository;
    private final EmbeddingModel embeddingModel;
    private final TaskScheduler scheduler;
    private final ChatbotProperties props;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ItemEmbeddingJob(CatalogReadPort catalog, ItemEmbeddingRepository repository,
                            EmbeddingModel embeddingModel, TaskScheduler scheduler, ChatbotProperties props,
                            Clock clock) {
        this.catalog = catalog;
        this.repository = repository;
        this.embeddingModel = embeddingModel;
        this.scheduler = scheduler;
        this.props = props;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void schedule() {
        if (!props.chatbot().embedding().enabled()) {
            return;
        }
        scheduler.scheduleWithFixedDelay(this::refreshSafely, clock.instant(),
                props.chatbot().embedding().refreshInterval());
    }

    void refreshSafely() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("Chưa cập nhật được embedding sản phẩm/combo: {}", e.getMessage());
        }
    }

    /** @return số mặt hàng đã embed lại. */
    public int refresh() {
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            Map<Long, String> products = new LinkedHashMap<>();
            catalog.allActiveProducts().forEach(p -> products.put(p.card().id(), ItemTextBuilder.product(p)));
            Map<Long, String> combos = new LinkedHashMap<>();
            catalog.allActiveCombos().forEach(c -> combos.put(c.id(), ItemTextBuilder.combo(c)));
            int changed = sync(ItemType.PRODUCT, products) + sync(ItemType.COMBO, combos);
            if (changed > 0) {
                log.info("Đã embed lại {} sản phẩm/combo", changed);
            }
            return changed;
        } finally {
            running.set(false);
        }
    }

    private int sync(ItemType type, Map<Long, String> texts) {
        repository.deleteExcept(type, texts.keySet());
        Map<Long, String> existing = repository.hashes(type);
        List<Long> ids = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        texts.forEach((id, text) -> {
            String hash = sha256(text);
            if (!hash.equals(existing.get(id))) {
                ids.add(id);
                hashes.add(hash);
            }
        });
        for (int from = 0; from < ids.size(); from += BATCH) {
            int to = Math.min(from + BATCH, ids.size());
            List<String> batch = ids.subList(from, to).stream().map(texts::get).toList();
            List<float[]> vectors = embeddingModel.embed(batch);
            for (int i = 0; i < vectors.size(); i++) {
                repository.upsert(type, ids.get(from + i), hashes.get(from + i), vectors.get(i));
            }
        }
        return ids.size();
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
