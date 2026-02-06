package kr.go.growailms.tutor.common;

/**
 * 왜: init.jsp가 JSP 세션에서 userId/siteId/userKind를 꺼내 매 요청마다 설정하던 역할을
 *     Spring Boot에서는 ThreadLocal로 대체합니다.
 *     인터셉터(TutorAuthInterceptor)가 요청 시작 시 set(), 종료 시 clear()합니다.
 */
public final class TutorAuthContext {

    private static final ThreadLocal<AuthInfo> HOLDER = new ThreadLocal<>();

    private TutorAuthContext() {
    }

    // 왜: 인터셉터에서 인증 완료 후 호출합니다.
    public static void set(AuthInfo info) {
        HOLDER.set(info);
    }

    public static AuthInfo get() {
        AuthInfo info = HOLDER.get();
        if (info == null) {
            throw new IllegalStateException("TutorAuthContext가 설정되지 않았습니다. 인터셉터를 거치지 않은 요청입니다.");
        }
        return info;
    }

    // 왜: 요청 완료 후 반드시 호출하여 메모리 누수를 방지합니다.
    public static void clear() {
        HOLDER.remove();
    }

    public static long userId() {
        return get().userId();
    }

    public static long siteId() {
        return get().siteId();
    }

    public static boolean isAdmin() {
        return get().isAdmin();
    }

    public static String userKind() {
        return get().userKind();
    }

    /**
     * 왜: init.jsp의 userId, siteId, userKind, isAdmin 4가지를 불변 레코드로 묶습니다.
     *     isAdmin = "S".equals(userKind) || "A".equals(userKind)
     */
    public record AuthInfo(
            long userId,
            long siteId,
            String userKind,
            boolean isAdmin
    ) {
    }
}
