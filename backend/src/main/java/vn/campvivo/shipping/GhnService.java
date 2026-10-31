package vn.campvivo.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.shipping.dto.DistrictResponse;
import vn.campvivo.shipping.dto.ProvinceResponse;
import vn.campvivo.shipping.dto.ShippingServiceResponse;
import vn.campvivo.shipping.dto.WardResponse;

import java.text.Collator;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client GHN (D-27): danh mục địa chỉ, gói dịch vụ, tính phí, tạo vận đơn, xem chi tiết vận đơn.
 * Token/ShopId chỉ nằm ở server (app.ghn.*, biến môi trường GHN_*), không bao giờ trả ra API hay ghi log.
 * Mọi lỗi từ GHN (mạng, HTTP 4xx/5xx, code ≠ 200) → SHIPPING_GATEWAY_ERROR; chi tiết chỉ ghi log phía server.
 * Danh mục tỉnh/quận/phường ít đổi nên được nhớ trong bộ nhớ app.ghn.master-data-cache-ttl.
 */
@Service
public class GhnService {

    private static final Logger log = LoggerFactory.getLogger(GhnService.class);
    private static final String API_PREFIX = "/shiip/public-api";

    private final AppProperties.Ghn props;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RestClient http;
    private final Map<String, CachedList> masterData = new ConcurrentHashMap<>();
    private final Comparator<String> vietnameseOrder;

    public GhnService(AppProperties appProperties, ObjectMapper objectMapper, Clock clock) {
        this.props = appProperties.ghn();
        this.objectMapper = objectMapper;
        this.clock = clock;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.timeout());
        requestFactory.setReadTimeout(props.timeout());
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl().replaceAll("/+$", "") + API_PREFIX)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        this.vietnameseOrder = collator::compare;
    }

    // ---------------------------------------------------------------- danh mục địa chỉ

    public List<ProvinceResponse> getProvinces() {
        return cached("provinces", () -> {
            List<ProvinceResponse> result = new ArrayList<>();
            for (JsonNode p : call("/master-data/province", null, false)) {
                String name = p.path("ProvinceName").asText("");
                // Staging của GHN có tỉnh thử nghiệm ("Test - ...") — không hiện cho khách
                if (isEnabled(p) && !name.isBlank() && !name.toLowerCase(Locale.ROOT).startsWith("test")) {
                    result.add(new ProvinceResponse(p.path("ProvinceID").asInt(), name));
                }
            }
            result.sort(Comparator.comparing(ProvinceResponse::name, vietnameseOrder));
            return result;
        });
    }

    public List<DistrictResponse> getDistricts(int provinceId) {
        return cached("districts:" + provinceId, () -> {
            List<DistrictResponse> result = new ArrayList<>();
            for (JsonNode d : call("/master-data/district", Map.of("province_id", provinceId), false)) {
                if (isEnabled(d)) {
                    result.add(new DistrictResponse(d.path("DistrictID").asInt(), d.path("DistrictName").asText("")));
                }
            }
            result.sort(Comparator.comparing(DistrictResponse::name, vietnameseOrder));
            return result;
        });
    }

    public List<WardResponse> getWards(int districtId) {
        return cached("wards:" + districtId, () -> {
            List<WardResponse> result = new ArrayList<>();
            for (JsonNode w : call("/master-data/ward", Map.of("district_id", districtId), false)) {
                if (isEnabled(w)) {
                    result.add(new WardResponse(w.path("WardCode").asText(""), w.path("WardName").asText("")));
                }
            }
            result.sort(Comparator.comparing(WardResponse::name, vietnameseOrder));
            return result;
        });
    }

    // ---------------------------------------------------------------- dịch vụ, phí, vận đơn

    /** Các gói dịch vụ GHN hỗ trợ từ kho (app.ghn.from-district-id) tới quận nhận. */
    public List<ShippingServiceResponse> getAvailableServices(int toDistrictId) {
        Map<String, Object> body = Map.of(
                "shop_id", shopIdAsNumber(),
                "from_district", props.fromDistrictId(),
                "to_district", toDistrictId);
        List<ShippingServiceResponse> result = new ArrayList<>();
        for (JsonNode s : call("/v2/shipping-order/available-services", body, false)) {
            result.add(new ShippingServiceResponse(
                    s.path("service_id").asInt(), s.path("service_type_id").asInt(), s.path("short_name").asText("")));
        }
        return result;
    }

    /** Phí GHN (VND) cho một kiện theo gói app.ghn.service-type-id. */
    public long calculateFee(GhnFeeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service_type_id", props.serviceTypeId());
        body.put("from_district_id", props.fromDistrictId());
        body.put("from_ward_code", props.fromWardCode());
        body.put("to_district_id", request.toDistrictId());
        body.put("to_ward_code", request.toWardCode());
        body.put("weight", request.weight());
        putParcelSize(body);
        body.put("insurance_value", request.insuranceValue());
        JsonNode data = call("/v2/shipping-order/fee", body, true);
        if (!data.hasNonNull("total")) {
            log.warn("GHN tính phí không trả trường total");
            throw gatewayError();
        }
        return data.path("total").asLong();
    }

    /** Tạo vận đơn. Shop trả phí GHN (payment_type_id = 1) vì khách đã trả phí ship trong tổng đơn. */
    public GhnCreateOrderResult createOrder(GhnCreateOrderRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payment_type_id", 1);
        body.put("required_note", props.requiredNote());
        body.put("client_order_code", request.clientOrderCode());
        body.put("to_name", request.toName());
        body.put("to_phone", request.toPhone());
        body.put("to_address", request.toAddress());
        body.put("to_ward_code", request.toWardCode());
        body.put("to_district_id", request.toDistrictId());
        body.put("cod_amount", request.codAmount());
        body.put("insurance_value", request.insuranceValue());
        body.put("weight", request.weight());
        putParcelSize(body);
        body.put("service_type_id", props.serviceTypeId());
        if (request.note() != null && !request.note().isBlank()) {
            body.put("note", request.note());
        }
        body.put("items", request.items().stream().map(i -> Map.of(
                "name", i.name(),
                "code", i.code(),
                "quantity", i.quantity(),
                "price", i.price(),
                "weight", i.weight())).toList());
        JsonNode data = call("/v2/shipping-order/create", body, true);
        String orderCode = data.path("order_code").asText("");
        if (orderCode.isBlank()) {
            log.warn("GHN tạo vận đơn cho {} không trả order_code", request.clientOrderCode());
            throw gatewayError();
        }
        return new GhnCreateOrderResult(orderCode, data.path("total_fee").asLong(),
                parseTime(data.path("expected_delivery_time")));
    }

    public GhnOrderDetail getOrderDetail(String orderCode) {
        JsonNode data = call("/v2/shipping-order/detail", Map.of("order_code", orderCode), true);
        List<GhnOrderDetail.LogEntry> history = new ArrayList<>();
        for (JsonNode entry : data.path("log")) {
            history.add(new GhnOrderDetail.LogEntry(entry.path("status").asText(""), parseTime(entry.path("updated_date"))));
        }
        return new GhnOrderDetail(
                data.path("order_code").asText(orderCode),
                data.path("client_order_code").asText(null),
                data.path("status").asText(""),
                parseTime(data.path("leadtime")),
                parseTime(data.path("updated_date")),
                history);
    }

    // ---------------------------------------------------------------- hạ tầng

    private JsonNode call(String path, Object body, boolean withShopId) {
        if (!props.configured()) {
            log.error("Chưa cấu hình app.ghn.* (GHN_TOKEN, GHN_SHOP_ID, GHN_FROM_DISTRICT_ID, GHN_FROM_WARD_CODE)");
            throw gatewayError();
        }
        try {
            JsonNode response = http.post()
                    .uri(path)
                    .headers(h -> {
                        h.set("Token", props.token());
                        if (withShopId) {
                            h.set("ShopId", props.shopId());
                        }
                    })
                    .body(body == null ? Map.of() : body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.path("code").asInt() != 200) {
                log.warn("GHN {} trả code {}: {}", path,
                        response == null ? "null" : response.path("code").asText(),
                        response == null ? "" : response.path("message").asText(""));
                throw gatewayError();
            }
            return response.path("data");
        } catch (RestClientResponseException e) {
            log.warn("GHN {} trả HTTP {}: {}", path, e.getStatusCode().value(), ghnMessage(e));
            throw gatewayError();
        } catch (RestClientException e) {
            log.error("Không gọi được GHN {}", path, e);
            throw gatewayError();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> cached(String key, Supplier<List<T>> loader) {
        Instant now = clock.instant();
        CachedList hit = masterData.get(key);
        if (hit != null && hit.expiresAt().isAfter(now)) {
            return (List<T>) hit.items();
        }
        List<T> items = List.copyOf(loader.get());
        // Danh sách rỗng thường do nhập sai id — không nhớ để lần sau còn hỏi lại GHN
        if (!items.isEmpty()) {
            masterData.put(key, new CachedList(items, now.plus(props.masterDataCacheTtl())));
        }
        return items;
    }

    private record CachedList(List<?> items, Instant expiresAt) {}

    private void putParcelSize(Map<String, Object> body) {
        body.put("length", props.parcelLength());
        body.put("width", props.parcelWidth());
        body.put("height", props.parcelHeight());
    }

    /** GHN không trả IsEnable với một số bản ghi cũ — coi là đang bật, chỉ bỏ bản ghi IsEnable = 0. */
    private static boolean isEnabled(JsonNode node) {
        return !node.has("IsEnable") || node.path("IsEnable").asInt() != 0;
    }

    private long shopIdAsNumber() {
        try {
            return Long.parseLong(props.shopId().trim());
        } catch (NumberFormatException e) {
            log.error("app.ghn.shop-id không phải số");
            throw gatewayError();
        }
    }

    /** Chỉ lấy trường message của GHN (không chứa token) để ghi log. */
    private String ghnMessage(RestClientResponseException e) {
        try {
            return objectMapper.readTree(e.getResponseBodyAsString()).path("message").asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static OffsetDateTime parseTime(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.asText());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BusinessException gatewayError() {
        return new BusinessException(ErrorCode.SHIPPING_GATEWAY_ERROR);
    }
}
