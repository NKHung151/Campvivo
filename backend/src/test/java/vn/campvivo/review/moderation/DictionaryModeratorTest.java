package vn.campvivo.review.moderation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Từ điển chính thức (moderation/vi-banned-words.txt) + quy tắc nạp file (D-33). Sửa từ điển → chạy lại cả 2 nhóm câu.
 * Thêm mục mới → thêm ít nhất 1 câu vào {@link #flagsOffensiveContent}.
 */
class DictionaryModeratorTest {

    private static final DictionaryModerator MODERATOR = new DictionaryModerator();

    private static DictionaryModerator dictionary(String text) {
        return DictionaryModerator.fromStream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "test");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // chửi thề + các kiểu lách
            "Đ.m hàng như c*t",
            "ngu vl",
            "D K M shop",
            "vcllll giao chậm",
            "dmm shop",
            "đmmm",
            "DCM giao chậm vậy",
            "...dkm!!!",
            "đ​m shop",
            "đ*t mẹ shop",
            "d1t m3 m4y",
            "đéo mua nữa",
            "Đéééo bao giờ quay lại",
            "đ.é.o thèm",
            "đù má giao sai hàng",
            "đụ mẹ",
            "clgt",
            "cmnr hỏng rồi",
            "mẹ kiếp",
            "con mẹ mày",
            "Tổ cha cái shop",
            "chó đẻ",
            // tình dục / bộ phận (nghĩa tục)
            "Lều như lồn",
            "lồn",
            "Hàng l0n gì đâu",
            "vãi l*n",
            "l.o.n",
            "c4c thật",
            "như cứt",
            "như c*t",
            "buồi",
            "chịch",
            "nứng",
            "loz",
            // lăng mạ
            "Shop này lừa đảo, đồ chó",
            "thằng ngu",
            "Thằng chủ shop ngu như bò",
            "óc chó à",
            "mất dạy",
            "Đồ súc vật",
            "khốn nạn",
            "cút đi đồ đĩ",
            "Con nhân viên như điếm",
            // kỳ thị
            "Bọn bắc kỳ bán hàng",
            "pê đê",
            "thiểu năng",
            "bại não",
            // đe doạ
            "Tao sẽ giết mày",
            "đốt nhà mày",
            "Mày chết với tao",
            // tiếng Anh
            "fuck this tent",
            "FUCKKKK",
            "f*ck",
            "holy shit",
            "wtf",
            "Bitch"
    })
    void flagsOffensiveContent(String content) {
        ModerationResult result = MODERATOR.check(content);
        assertThat(result.sensitive()).as(content).isTrue();
        assertThat(result.flaggedTerms()).as(content).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // chê bai gay gắt nhưng sạch
            "Lều tốt, dựng nhanh, đáng tiền",
            "Hàng tệ, shop lừa đảo, phí tiền",
            "Hàng dỏm, rác, không nên mua",
            "Giao chậm, đóng gói ẩu, thất vọng",
            // từ trùng khi bỏ dấu / gõ không dấu
            "Uống 1 lon nước rồi ngủ ngon trong lều",
            "Lon bia, lon nước ngọt đều vừa",
            "ngu ngon lam",
            "Mình hơi ngu ngơ khi dựng lều lần đầu",
            "Các bạn nên mua",
            "cac ban nen mua",
            "Đeo balo rất êm",
            "deo balo rat em",
            "Đi chơi đèo rất ổn",
            "di choi rat vui",
            "Điểm 10 cho chất lượng",
            "Điểm cộng là có túi đựng",
            "Du lịch Đà Lạt",
            "Đủ đồ cho cả nhà",
            "Buổi tối hơi lạnh",
            "buoi toi hoi lanh",
            "Muỗi chích nhiều, nên mang thuốc",
            "Nồi nung nóng nhanh",
            "Bu lông chắc chắn",
            "bu lon hoi long",
            "Hạt óc chó ngon",
            "Con chó nhà mình thích lều này",
            "Dễ dùng, mua cho dễ",
            "Hàng tốt dù mà giá hơi cao",
            "Lều bị cụt một cọc",
            "Cứt chim dính lên lều",
            "Thiếu năng lượng khi leo núi",
            "Phở ngon",
            "Tháng chờ hàng hơi lâu",
            "Giao tới Nam Kỳ Khởi Nghĩa nhanh",
            "Bắc Kạn giao nhanh",
            "Thằng bé nhà mình rất thích",
            "Con điện thoại sạc nhanh",
            // cụm "đồ" theo nghĩa đồ vật
            "Đồ bóng đá chất lượng",
            "Đồ ăn mang theo rất tiện",
            "Đồ mùa đông giữ ấm tốt",
            "Đồ câu cá đầy đủ",
            // câu có động từ mạnh nhưng không đe doạ
            "Tao giết muỗi bằng vợt điện",
            "Pin chết đi sống lại mấy lần",
            // viết tắt kỹ thuật, số đo, thương hiệu
            "admin trả lời nhanh",
            "Kích thước 200 cm x 150 cm",
            "Bình 750 ml, nặng 2 kg",
            "Đèn dmx, laptop Dell, xe 125cc",
            "Mã D1, dung tích 5L, lều 4m",
            "Lều 2*3m rất rộng",
            "Web có html lỗi, vote 5 sao"
    })
    void doesNotFlagNormalContent(String content) {
        ModerationResult result = MODERATOR.check(content);
        assertThat(result.flaggedTerms()).as(content).isEmpty();
        assertThat(result.sensitive()).as(content).isFalse();
    }

    @Test
    void blankContentIsSafe() {
        assertThat(MODERATOR.check(null)).isEqualTo(ModerationResult.SAFE);
        assertThat(MODERATOR.check("  ")).isEqualTo(ModerationResult.SAFE);
    }

    @Test
    void reportsOriginalEntriesWithoutDuplicates() {
        ModerationResult result = MODERATOR.check("đéo, đéo và đéo");
        assertThat(result.flaggedTerms()).containsExactly("đéo");
    }

    @Test
    void officialDictionaryIsSubstantial() {
        assertThat(MODERATOR.size()).isGreaterThan(150);
    }

    @Test
    void sectionsMatchByTheirRules() {
        DictionaryModerator d = dictionary("""
                [accented]
                lồn
                [plain]
                dit me
                [abbr]
                dm
                [allow]
                hạt lồn
                """);
        assertThat(d.check("lồn").sensitive()).isTrue();
        assertThat(d.check("lon").sensitive()).isFalse();          // không dấu, không biến dạng
        assertThat(d.check("l0n").sensitive()).isTrue();           // biến dạng có chủ ý
        assertThat(d.check("địt mẹ").flaggedTerms()).containsExactly("dit me");
        assertThat(d.check("admin").sensitive()).isFalse();
        assertThat(d.check("hạt lồn").sensitive()).isFalse();      // [allow]
    }

    @Test
    void flaggedTermsAreCappedAt500Characters() {
        // 40 mục × ~25 ký tự > 500; chỉ dùng chữ cái, không lặp 3 lần (tránh leetspeak/gộp chữ lặp)
        String entries = IntStream.range(0, 40).mapToObj(DictionaryModeratorTest::longWord).collect(Collectors.joining("\n"));
        String text = IntStream.range(0, 40).mapToObj(DictionaryModeratorTest::longWord).collect(Collectors.joining(" "));
        ModerationResult result = dictionary("[plain]\n" + entries).check(text);
        assertThat(result.sensitive()).isTrue();
        assertThat(String.join(",", result.flaggedTerms()).length()).isLessThanOrEqualTo(500);
    }

    private static String longWord(int i) {
        return "tu" + (char) ('a' + i / 26) + (char) ('a' + i % 26) + "bcdefghijklmnopqrstuvw";
    }

    @Test
    void unknownSectionIsRejected() {
        assertThatThrownBy(() -> dictionary("[badwords]\nabc"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dòng 1");
    }

    @Test
    void entryOutsideSectionIsRejected() {
        assertThatThrownBy(() -> dictionary("# chú thích\nabc\n[plain]\nxyz"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dòng 2");
    }

    @Test
    void multiWordAbbreviationIsRejected() {
        assertThatThrownBy(() -> dictionary("[abbr]\nd m"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("[abbr]");
    }

    @Test
    void accentedAbbreviationIsRejected() {
        assertThatThrownBy(() -> dictionary("[abbr]\nđm"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("[abbr]");
    }

    @Test
    void dictionaryWithoutBlockedEntriesIsRejected() {
        assertThatThrownBy(() -> dictionary("[allow]\nhạt óc chó"))
                .isInstanceOf(IllegalStateException.class);
    }
}
