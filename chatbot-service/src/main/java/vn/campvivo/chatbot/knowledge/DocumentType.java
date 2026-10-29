package vn.campvivo.chatbot.knowledge;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Loại tài liệu nhận được (api.md 4.12). Xác định theo đuôi file và kiểm chữ ký đầu file cho PDF/DOCX
 * để không nhận file đổi đuôi.
 */
public enum DocumentType {
    PDF(".pdf", "application/pdf"),
    DOCX(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    TXT(".txt", "text/plain"),
    MD(".md", "text/markdown");

    private final String extension;
    private final String contentType;

    DocumentType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<DocumentType> detect(String fileName, byte[] content) {
        if (fileName == null || content == null || content.length == 0) {
            return Optional.empty();
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(t -> lower.endsWith(t.extension))
                .findFirst()
                .filter(t -> t.matchesSignature(content));
    }

    public static Optional<DocumentType> fromContentType(String contentType) {
        return Arrays.stream(values()).filter(t -> t.contentType.equals(contentType)).findFirst();
    }

    private boolean matchesSignature(byte[] c) {
        return switch (this) {
            case PDF -> startsWith(c, new byte[]{'%', 'P', 'D', 'F'});
            case DOCX -> startsWith(c, new byte[]{'P', 'K', 3, 4});   // .docx là file zip
            case TXT, MD -> true;
        };
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
