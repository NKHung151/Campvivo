package vn.campvivo.cart;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.cart.dto.CartRequests;
import vn.campvivo.cart.dto.CartResponse;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductService;
import vn.campvivo.combo.ComboService;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.inventory.InventoryService;
import vn.campvivo.promotion.PricingService;


import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Giỏ hàng (FR-09, api.md 4.4). Giá luôn tính lại bằng PricingService (D-04). Thêm/sửa khoá dòng `carts` của user để
 * các request song song của cùng một người không vướng unique; số lượng kiểm theo BR-1 (gồm số đã có trong giỏ).
 */
@Service
public class CartService {

    /** Dòng giỏ đem đi đặt hàng. */
    public record CheckoutLine(Long cartItemId, Long productId, Long comboId, int quantity) {}

    private final CartRepository cartRepository;
    private final EntityManager em;
    private final PricingService pricingService;
    private final ProductService productService;
    private final ComboService comboService;
    private final Clock clock;

    public CartService(CartRepository cartRepository, EntityManager em, PricingService pricingService,
                       ProductService productService, ComboService comboService, Clock clock) {
        this.cartRepository = cartRepository;
        this.em = em;
        this.pricingService = pricingService;
        this.productService = productService;
        this.comboService = comboService;
        this.clock = clock;
    }

    @Transactional
    public CartResponse getCart(Long userId) {
        return toResponse(lockCart(userId));
    }

    @Transactional
    public CartResponse addItem(Long userId, CartRequests.AddItem req) {
        Cart cart = lockCart(userId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        CartItem existing = cart.getItems().stream()
                .filter(i -> req.productId() != null ? i.isProduct() && i.getProduct().getId().equals(req.productId())
                        : !i.isProduct() && i.getCombo().getId().equals(req.comboId()))
                .findFirst().orElse(null);
        int newQuantity = req.quantity() + (existing == null ? 0 : existing.getQuantity());

        if (req.productId() != null) {
            Product product = em.find(Product.class, req.productId());
            ensureSellable(product == null ? null : product.isActive(), "PRODUCT", req.productId(),
                    product == null ? null : product.getName(), product == null ? 0 : product.getAvailableQuantity(), newQuantity);
            if (existing == null) {
                cart.getItems().add(new CartItem(cart, product, null, newQuantity, now));
            } else {
                existing.changeQuantity(newQuantity, now);
            }
        } else {
            ProductCombo combo = em.find(ProductCombo.class, req.comboId());
            ensureSellable(combo == null ? null : combo.isActive(), "COMBO", req.comboId(),
                    combo == null ? null : combo.getName(), combo == null ? 0 : combo.getAvailableQuantity(), newQuantity);
            if (existing == null) {
                cart.getItems().add(new CartItem(cart, null, combo, newQuantity, now));
            } else {
                existing.changeQuantity(newQuantity, now);
            }
        }
        cart.touch(now);
        cartRepository.flush();
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long cartItemId, int quantity) {
        Cart cart = lockCart(userId);
        CartItem item = findLine(cart, cartItemId);
        if (item.isProduct()) {
            Product p = item.getProduct();
            ensureSellable(p.isActive(), "PRODUCT", p.getId(), p.getName(), p.getAvailableQuantity(), quantity);
        } else {
            ProductCombo c = item.getCombo();
            ensureSellable(c.isActive(), "COMBO", c.getId(), c.getName(), c.getAvailableQuantity(), quantity);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        item.changeQuantity(quantity, now);
        cart.touch(now);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long cartItemId) {
        Cart cart = lockCart(userId);
        cart.getItems().remove(findLine(cart, cartItemId));
        cart.touch(OffsetDateTime.now(clock));
        return toResponse(cart);
    }

    /**
     * Các dòng sẽ đặt hàng. `cartItemIds` null/rỗng = cả giỏ; id không thuộc giỏ của user → NOT_FOUND.
     * Giỏ (hoặc lựa chọn) rỗng → CART_EMPTY.
     */
    @Transactional(readOnly = true)
    public List<CheckoutLine> checkoutLines(Long userId, Collection<Long> cartItemIds) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null || cart.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }
        Set<Long> wanted = cartItemIds == null ? Set.of() : new HashSet<>(cartItemIds);
        List<CheckoutLine> lines = cart.getItems().stream()
                .filter(i -> wanted.isEmpty() || wanted.contains(i.getId()))
                .map(i -> new CheckoutLine(i.getId(),
                        i.isProduct() ? i.getProduct().getId() : null,
                        i.isProduct() ? null : i.getCombo().getId(),
                        i.getQuantity()))
                .toList();
        if (lines.size() < wanted.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }
        return lines;
    }

    /** Xoá các dòng đã đặt hàng (trong transaction tạo đơn). */
    @Transactional
    public void removeItems(Long userId, Collection<Long> cartItemIds) {
        cartRepository.findByUserIdForUpdate(userId).ifPresent(cart -> {
            cart.getItems().removeIf(i -> cartItemIds.contains(i.getId()));
            cart.touch(OffsetDateTime.now(clock));
        });
    }

    // ------------------------------------------------------------------

    private Cart lockCart(Long userId) {
        cartRepository.insertIfAbsent(userId);
        return cartRepository.findByUserIdForUpdate(userId).orElseThrow();
    }

    private static CartItem findLine(Cart cart, Long cartItemId) {
        return cart.getItems().stream()
                .filter(i -> i.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** Không tồn tại → NOT_FOUND; ngừng bán → PRODUCT_INACTIVE; vượt khả dụng → OUT_OF_STOCK (BR-1, BR-14). */
    private static void ensureSellable(Boolean active, String type, Long id, String name, int available, int wanted) {
        if (active == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!active) {
            throw new BusinessException(ErrorCode.PRODUCT_INACTIVE);
        }
        if (wanted > available) {
            throw InventoryService.outOfStock(List.of(InventoryService.shortage(type, id, name, available)));
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<Long> productIds = cart.getItems().stream().filter(CartItem::isProduct).map(i -> i.getProduct().getId()).toList();
        List<Long> comboIds = cart.getItems().stream().filter(i -> !i.isProduct()).map(i -> i.getCombo().getId()).toList();
        Map<Long, String> productImages = productService.primaryImageUrls(productIds);
        Map<Long, String> comboImages = comboService.coverImageUrls(comboIds);

        BigDecimal subtotal = BigDecimal.ZERO;
        int itemCount = 0;
        boolean problems = false;
        List<CartResponse.Line> lines = new ArrayList<>();
        for (CartItem i : cart.getItems()) {
            CartResponse.Item item;
            if (i.isProduct()) {
                Product p = i.getProduct();
                item = new CartResponse.Item("PRODUCT", p.getId(), p.getName(), p.getSlug(), productImages.get(p.getId()),
                        p.getPrice(), pricingService.unitPrice(p), p.getAvailableQuantity(), null, 0, p.getBrand(), p.isActive());
            } else {
                ProductCombo c = i.getCombo();
                item = new CartResponse.Item("COMBO", c.getId(), c.getName(), c.getSlug(), comboImages.get(c.getId()),
                        c.getComboPrice(), pricingService.unitPrice(c), c.getAvailableQuantity(), null, 0, null, c.isActive());
            }
            BigDecimal lineTotal = item.price().multiply(BigDecimal.valueOf(i.getQuantity()));
            subtotal = subtotal.add(lineTotal);
            itemCount += i.getQuantity();
            problems |= !item.isActive() || i.getQuantity() > item.availableQuantity();
            lines.add(new CartResponse.Line(i.getId(), item, i.getQuantity(), lineTotal));
        }
        return new CartResponse(lines, subtotal, itemCount, problems);
    }
}
