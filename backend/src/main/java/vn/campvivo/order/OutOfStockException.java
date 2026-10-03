package vn.campvivo.order;

import org.springframework.http.HttpStatus;
import vn.campvivo.common.exception.BusinessException;

/** errorCode OUT_OF_STOCK — khớp docs/02-ky-thuat/api.md (POST /cart/items, POST /orders). */
public class OutOfStockException extends BusinessException {
    public OutOfStockException(String productName) {
        super("OUT_OF_STOCK", "Sản phẩm \"" + productName + "\" không đủ số lượng khả dụng.",
                HttpStatus.CONFLICT);
    }
}
