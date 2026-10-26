package vn.campvivo.common.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void computesTotalPagesFromTotalItems() {
        PageResponse<String> res = PageResponse.of(List.of("a"), 1, 12, 25);

        assertThat(res.success()).isTrue();
        assertThat(res.pagination().totalPages()).isEqualTo(3);
        assertThat(res.pagination().totalItems()).isEqualTo(25);
        assertThat(res.pagination().limit()).isEqualTo(12);
    }

    @Test
    void convertsSpringZeroBasedPageToOneBased() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 12), 25);

        PageResponse<String> res = PageResponse.of(page);

        assertThat(res.pagination().currentPage()).isEqualTo(1);
        assertThat(res.pagination().totalPages()).isEqualTo(3);
        assertThat(res.data()).containsExactly("a", "b");
    }

    @Test
    void emptyResultHasZeroPages() {
        PageResponse<String> res = PageResponse.of(List.of(), 1, 12, 0);

        assertThat(res.pagination().totalPages()).isZero();
        assertThat(res.data()).isEmpty();
    }

    @Test
    void exactMultipleDoesNotAddExtraPage() {
        assertThat(PageResponse.of(List.of(), 1, 12, 24).pagination().totalPages()).isEqualTo(2);
    }
}
