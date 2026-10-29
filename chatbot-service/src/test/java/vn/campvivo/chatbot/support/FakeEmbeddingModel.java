package vn.campvivo.chatbot.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedding giả, tất định: "túi từ" không dấu băm vào 1536 chiều rồi chuẩn hoá — văn bản chung nhiều từ thì gần nhau.
 * Đếm số văn bản đã embed để test job chỉ embed lại khi đổi. `fail = true` → ném lỗi như OpenAI sập.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 1536;

    public final AtomicInteger embeddedTexts = new AtomicInteger();
    public volatile boolean fail;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (fail) {
            throw new IllegalStateException("embedding giả đang lỗi");
        }
        List<Embedding> result = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            embeddedTexts.incrementAndGet();
            result.add(new Embedding(vector(texts.get(i)), i));
        }
        return new EmbeddingResponse(result);
    }

    @Override
    public float[] embed(Document document) {
        return call(new EmbeddingRequest(List.of(document.getText()), null)).getResult().getOutput();
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public static float[] vector(String text) {
        float[] v = new float[DIMENSIONS];
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd');
        for (String token : normalized.split("[^a-z0-9]+")) {
            if (token.length() > 1) {
                v[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1f;
            return v;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= n;
        }
        return v;
    }
}
