package vn.campvivo.chatbot.common.util;

/**
 * Đổi vector embedding sang dạng chữ của pgvector (`[0.1,0.2,...]`) để truyền qua JDBC rồi `CAST(? AS vector)`.
 * Không cần thư viện pgvector-java.
 */
public final class PgVector {

    private PgVector() {
    }

    public static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10 + 2).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
