package vn.campvivo.chatbot.common.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Thời gian trả ra API theo giờ Việt Nam (`+07:00`, api.md mục 1). Driver PostgreSQL trả timestamptz ở UTC.
 */
public final class Times {

    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private Times() {
    }

    public static OffsetDateTime vn(ResultSet rs, String column) throws SQLException {
        OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
        return t == null ? null : t.atZoneSameInstant(VN).toOffsetDateTime();
    }
}
