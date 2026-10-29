package vn.campvivo.chatbot.advisor;

/**
 * Loại chuyến đi khách nêu khi xin tư vấn. Mỗi loại có một mẫu nhu cầu trong `chatbot/trip-templates.yml`.
 */
public enum TripType {
    CAMPING,
    PICNIC,
    TREKKING_DAY,
    TREKKING_OVERNIGHT,
    BEACH
}
