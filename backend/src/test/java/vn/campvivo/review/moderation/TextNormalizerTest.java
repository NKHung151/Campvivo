package vn.campvivo.review.moderation;

import org.junit.jupiter.api.Test;
import vn.campvivo.review.moderation.TextNormalizer.Token;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private static List<String> words(String raw) {
        return TextNormalizer.tokenize(raw).stream().map(Token::accented).toList();
    }

    private static Token only(String raw) {
        List<Token> tokens = TextNormalizer.tokenize(raw);
        assertThat(tokens).hasSize(1);
        return tokens.get(0);
    }

    @Test
    void cleanComposesDiacriticsRemovesInvisibleAndLowercases() {
        assertThat(TextNormalizer.clean("lồn")).isEqualTo("lồn");        // gõ dấu tổ hợp
        assertThat(TextNormalizer.clean("đ​m﻿")).isEqualTo("đm");
        assertThat(TextNormalizer.clean("ĐM Shop")).isEqualTo("đm shop");
    }

    @Test
    void removeDiacriticsHandlesVietnamese() {
        assertThat(TextNormalizer.removeDiacritics("đường đèo ngủ ngon")).isEqualTo("duong deo ngu ngon");
        assertThat(TextNormalizer.removeDiacritics("Đặng")).isEqualTo("Dang");
    }

    @Test
    void blankInputHasNoTokens() {
        assertThat(TextNormalizer.tokenize(null)).isEmpty();
        assertThat(TextNormalizer.tokenize("   \n ")).isEmpty();
        assertThat(TextNormalizer.tokenize("!!! ... ???")).isEmpty();
    }

    @Test
    void splitsOnPunctuationAndKeepsNormalWords() {
        assertThat(words("Lều tốt, dựng nhanh!")).containsExactly("lều", "tốt", "dựng", "nhanh");
        assertThat(only("admin").obfuscated()).isFalse();
    }

    @Test
    void joinsSingleLettersSeparatedBySymbolsOrSpaces() {
        assertThat(words("đ.m")).containsExactly("đm");
        assertThat(words("D K M shop")).containsExactly("dkm", "shop");
        assertThat(words("d - k - m")).containsExactly("dkm");
        assertThat(words("v.c.l")).containsExactly("vcl");
        assertThat(only("đ.m").obfuscated()).isTrue();
    }

    @Test
    void doesNotJoinSingleLetterWithLongerWord() {
        assertThat(words("size m rất vừa")).containsExactly("size", "m", "rất", "vừa");
    }

    @Test
    void leetspeakOnlyWhenTwoOrMoreLetters() {
        Token t = only("l0n");
        assertThat(t.accented()).isEqualTo("lon");
        assertThat(t.obfuscated()).isTrue();
        assertThat(words("D1 5L 4m")).containsExactly("d1", "5l", "4m");
        assertThat(TextNormalizer.tokenize("D1 5L 4m")).noneMatch(Token::obfuscated);
    }

    @Test
    void plainNumbersAndWordsAreNotObfuscated() {
        List<Token> tokens = TextNormalizer.tokenize("1 lon nước");
        assertThat(tokens).extracting(Token::accented).containsExactly("1", "lon", "nước");
        assertThat(tokens).noneMatch(Token::obfuscated);
    }

    @Test
    void collapsesCharactersRepeatedThreeTimesOrMore() {
        assertThat(words("nguuuu vcllll Đéééo")).containsExactly("ngu", "vcl", "đéo");
        assertThat(words("xoong")).containsExactly("xoong");                         // lặp 2 lần giữ nguyên
    }

    @Test
    void maskedLettersBecomeWildcard() {
        Token t = only("c*t");
        assertThat(t.accented()).isEqualTo("ct");
        assertThat(t.obfuscated()).isTrue();
        assertThat(t.maskMatches("cut")).isTrue();
        assertThat(t.maskMatches("cat")).isTrue();
        assertThat(t.maskMatches("ca")).isFalse();
        assertThat(only("f**k").maskMatches("fuck")).isTrue();
    }

    @Test
    void leadingAndTrailingAsterisksAreNotMasks() {
        Token t = only("*hello*");
        assertThat(t.accented()).isEqualTo("hello");
        assertThat(t.mask()).isNull();
    }

    @Test
    void plainFormDropsDiacritics() {
        assertThat(only("Đéo").plain()).isEqualTo("deo");
    }
}
