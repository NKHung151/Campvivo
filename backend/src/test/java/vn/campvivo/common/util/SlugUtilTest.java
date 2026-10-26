package vn.campvivo.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilTest {

    @Test
    void removesVietnameseDiacritics() {
        assertThat(SlugUtil.toSlug("Lều cắm trại 2 người")).isEqualTo("leu-cam-trai-2-nguoi");
    }

    @Test
    void convertsDWithStroke() {
        assertThat(SlugUtil.toSlug("Đồ Dã Ngoại!!")).isEqualTo("do-da-ngoai");
    }

    @Test
    void collapsesSeparatorsAndTrimsEdges() {
        assertThat(SlugUtil.toSlug("  --Túi   ngủ -- mùa đông--  ")).isEqualTo("tui-ngu-mua-dong");
    }

    @Test
    void returnsEmptyForNullBlankOrSymbolsOnly() {
        assertThat(SlugUtil.toSlug(null)).isEmpty();
        assertThat(SlugUtil.toSlug("   ")).isEmpty();
        assertThat(SlugUtil.toSlug("!!!")).isEmpty();
    }
}
