package vn.campvivo.common.upload;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.campvivo.common.exception.BusinessException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};
    static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a'};

    private String storedFolder;
    private final ImageUploadService service = new ImageUploadService((bytes, folder) -> {
        storedFolder = folder;
        return "https://res.cloudinary.com/demo/image/upload/x.png";
    });

    @Test
    void acceptsPngJpgWebpByContentNotByName() {
        assertThat(service.upload(file("anh.gif", "image/gif", PNG), "products")).startsWith("https://");
        assertThat(storedFolder).isEqualTo("products");
        assertThat(ImageUploadService.isSupportedImage(JPG)).isTrue();
        assertThat(ImageUploadService.isSupportedImage(WEBP)).isTrue();
    }

    @Test
    void rejectsOtherFormatsEvenIfNamedPng() {
        assertThatThrownBy(() -> service.upload(file("anh.png", "image/png", GIF), "products"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(fieldError(e)).isEqualTo("Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP."));
    }

    @Test
    void rejectsTooLargeEmptyAndUnknownFolder() {
        byte[] big = new byte[(int) ImageUploadService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertThatThrownBy(() -> service.upload(file("big.png", "image/png", big), "products"))
                .satisfies(e -> assertThat(fieldError(e)).isEqualTo("Ảnh vượt quá 5MB."));
        assertThatThrownBy(() -> service.upload(file("e.png", "image/png", new byte[0]), "products"))
                .satisfies(e -> assertThat(fieldError(e)).isEqualTo("Vui lòng chọn một ảnh."));
        assertThatThrownBy(() -> service.upload(file("a.png", "image/png", PNG), "../etc"))
                .isInstanceOf(BusinessException.class);
    }

    private static MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    @SuppressWarnings("unchecked")
    private static String fieldError(Throwable e) {
        Map<String, Map<String, String>> details = (Map<String, Map<String, String>>) ((BusinessException) e).getDetails();
        return details.get("fieldErrors").values().iterator().next();
    }
}
