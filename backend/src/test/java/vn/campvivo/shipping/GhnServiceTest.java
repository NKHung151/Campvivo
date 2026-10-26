package vn.campvivo.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.shipping.dto.ProvinceResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GhnService gọi một HTTP server giả lập GHN (đi qua HTTP thật, không gọi GHN thật). */
class GhnServiceTest {

    private static final String API = "/shiip/public-api";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Stub> stubs = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final Map<String, Recorded> lastRequest = new ConcurrentHashMap<>();
    private HttpServer server;

    private record Stub(int status, String body) {}

    private record Recorded(String token, String shopId, JsonNode body) {}

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            byte[] raw = exchange.getRequestBody().readAllBytes();
            lastRequest.put(path, new Recorded(
                    exchange.getRequestHeaders().getFirst("Token"),
                    exchange.getRequestHeaders().getFirst("ShopId"),
                    raw.length == 0 ? mapper.createObjectNode() : mapper.readTree(raw)));
            Stub stub = stubs.getOrDefault(path, new Stub(404, "{\"code\":404,\"message\":\"not found\"}"));
            byte[] out = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stub.status(), out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GhnService service(boolean configured) {
        Map<String, String> values = new HashMap<>();
        values.put("app.jwt.secret", "test_only_secret_at_least_32_characters_long");
        values.put("app.ghn.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
        if (configured) {
            values.put("app.ghn.token", "test-token");
            values.put("app.ghn.shop-id", "123");
            values.put("app.ghn.from-district-id", "1486");
            values.put("app.ghn.from-ward-code", "1A0404");
        }
        AppProperties props = new Binder(new MapConfigurationPropertySource(values))
                .bind("app", AppProperties.class).get();
        return new GhnService(props, mapper, Clock.systemUTC());
    }

    private void stub(String path, int status, String body) {
        stubs.put(API + path, new Stub(status, body));
    }

    private int hitCount(String path) {
        AtomicInteger n = hits.get(API + path);
        return n == null ? 0 : n.get();
    }

    @Test
    void provincesSkipDisabledAndTestEntriesSortVietnameseAndAreCached() {
        stub("/master-data/province", 200, """
                {"code":200,"message":"Success","data":[
                  {"ProvinceID":202,"ProvinceName":"Hồ Chí Minh","IsEnable":1},
                  {"ProvinceID":298,"ProvinceName":"Test - Alert - Tỉnh - 001","IsEnable":1},
                  {"ProvinceID":201,"ProvinceName":"Hà Nội","IsEnable":1},
                  {"ProvinceID":999,"ProvinceName":"Tỉnh đã tắt","IsEnable":0},
                  {"ProvinceID":203,"ProvinceName":"Đà Nẵng"},
                  {"ProvinceID":220,"ProvinceName":"Cần Thơ","IsEnable":1}]}""");
        GhnService ghn = service(true);

        List<ProvinceResponse> first = ghn.getProvinces();
        List<ProvinceResponse> second = ghn.getProvinces();

        assertThat(first).extracting(ProvinceResponse::name)
                .containsExactly("Cần Thơ", "Đà Nẵng", "Hà Nội", "Hồ Chí Minh");
        assertThat(second).isEqualTo(first);
        assertThat(hitCount("/master-data/province")).isEqualTo(1);
        assertThat(lastRequest.get(API + "/master-data/province").token()).isEqualTo("test-token");
    }

    @Test
    void districtsAndWardsSendParentId() {
        stub("/master-data/district", 200, """
                {"code":200,"data":[{"DistrictID":1442,"DistrictName":"Quận 1","IsEnable":1}]}""");
        stub("/master-data/ward", 200, """
                {"code":200,"data":[{"WardCode":"20109","WardName":"Phường Bến Nghé","IsEnable":1}]}""");
        GhnService ghn = service(true);

        assertThat(ghn.getDistricts(202)).singleElement().satisfies(d -> assertThat(d.id()).isEqualTo(1442));
        assertThat(ghn.getWards(1442)).singleElement().satisfies(w -> assertThat(w.code()).isEqualTo("20109"));
        assertThat(lastRequest.get(API + "/master-data/district").body().path("province_id").asInt()).isEqualTo(202);
        assertThat(lastRequest.get(API + "/master-data/ward").body().path("district_id").asInt()).isEqualTo(1442);
    }

    @Test
    void feeSendsShopCredentialsWarehouseAndParcel() {
        stub("/v2/shipping-order/fee", 200, """
                {"code":200,"message":"Success","data":{"total":36300,"service_fee":36300}}""");

        long fee = service(true).calculateFee(new GhnFeeRequest(1442, "20109", 1500, 0));

        assertThat(fee).isEqualTo(36300);
        Recorded req = lastRequest.get(API + "/v2/shipping-order/fee");
        assertThat(req.token()).isEqualTo("test-token");
        assertThat(req.shopId()).isEqualTo("123");
        assertThat(req.body().path("from_district_id").asInt()).isEqualTo(1486);
        assertThat(req.body().path("from_ward_code").asText()).isEqualTo("1A0404");
        assertThat(req.body().path("to_ward_code").asText()).isEqualTo("20109");
        assertThat(req.body().path("weight").asInt()).isEqualTo(1500);
        assertThat(req.body().path("service_type_id").asInt()).isEqualTo(2);
        assertThat(req.body().has("length")).isTrue();
    }

    @Test
    void ghnHttpErrorBecomesShippingGatewayError() {
        stub("/v2/shipping-order/fee", 400, """
                {"code":400,"message":"to ward not found","data":null}""");

        assertThatThrownBy(() -> service(true).calculateFee(new GhnFeeRequest(1442, "99999", 500, 0)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("SHIPPING_GATEWAY_ERROR"));
    }

    @Test
    void missingConfigurationFailsWithoutCallingGhn() {
        assertThatThrownBy(() -> service(false).getProvinces())
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("SHIPPING_GATEWAY_ERROR"));
        assertThat(hits).isEmpty();
    }

    @Test
    void createOrderAndDetailAreParsed() {
        stub("/v2/shipping-order/create", 200, """
                {"code":200,"message":"Success","data":{"order_code":"L8EAQ4","total_fee":36300,
                 "expected_delivery_time":"2026-10-28T16:59:59Z"}}""");
        stub("/v2/shipping-order/detail", 200, """
                {"code":200,"message":"Success","data":{"order_code":"L8EAQ4","client_order_code":"ORD-1",
                 "status":"ready_to_pick","leadtime":"2026-10-28T16:59:59Z","updated_date":"2026-10-26T05:59:42.108Z",
                 "log":[{"status":"ready_to_pick","updated_date":"2026-10-26T05:59:42.108Z"}]}}""");
        GhnService ghn = service(true);

        GhnCreateOrderResult created = ghn.createOrder(new GhnCreateOrderRequest(
                "ORD-1", "Nguyễn Văn A", "0901234567", "12 Lê Lợi", "20109", 1442, 500000, 0, 1000, null,
                List.of(new GhnCreateOrderRequest.Item("Lều 2 người", "P1", 2, 250000, 500))));
        GhnOrderDetail detail = ghn.getOrderDetail("L8EAQ4");

        assertThat(created.orderCode()).isEqualTo("L8EAQ4");
        assertThat(created.totalFee()).isEqualTo(36300);
        assertThat(created.expectedDeliveryTime()).isEqualTo(OffsetDateTime.parse("2026-10-28T16:59:59Z"));
        JsonNode body = lastRequest.get(API + "/v2/shipping-order/create").body();
        assertThat(body.path("client_order_code").asText()).isEqualTo("ORD-1");
        assertThat(body.path("cod_amount").asLong()).isEqualTo(500000);
        assertThat(body.path("payment_type_id").asInt()).isEqualTo(1);
        assertThat(body.path("items").get(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(body.has("note")).isFalse();

        assertThat(detail.status()).isEqualTo("ready_to_pick");
        assertThat(detail.clientOrderCode()).isEqualTo("ORD-1");
        assertThat(detail.log()).singleElement().satisfies(l -> assertThat(l.status()).isEqualTo("ready_to_pick"));
    }
}
