package kr.go.growailms.security.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * KISA SR1-2: SQL 삽입 공격 방지
 *
 * 안전한 동적 쿼리 생성을 위한 빌더 클래스
 * - Parameterized Query 강제
 * - 화이트리스트 기반 컬럼/테이블명 검증
 * - SQL Injection 패턴 탐지
 */
public final class SecureQueryBuilder {

    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
            "(?i)(--|;|/\\*|\\*/|xp_|sp_|exec|execute|union|select|insert|update|delete|drop|truncate|alter|create)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "=", "<>", "!=", "<", ">", "<=", ">=", "LIKE", "IN", "IS NULL", "IS NOT NULL", "BETWEEN"
    );

    private final StringBuilder queryBuffer;
    private final List<Object> parameterList;
    private final Set<String> allowedColumns;
    private final Set<String> allowedTables;

    private SecureQueryBuilder(Set<String> allowedColumns, Set<String> allowedTables) {
        this.queryBuffer = new StringBuilder(256);
        this.parameterList = new ArrayList<>();
        this.allowedColumns = allowedColumns != null ? allowedColumns : Collections.emptySet();
        this.allowedTables = allowedTables != null ? allowedTables : Collections.emptySet();
    }

    /**
     * 빌더 인스턴스 생성
     */
    public static SecureQueryBuilder create() {
        return new SecureQueryBuilder(null, null);
    }

    /**
     * 허용된 컬럼/테이블 목록과 함께 빌더 생성
     */
    public static SecureQueryBuilder createWithWhitelist(Set<String> allowedColumns, Set<String> allowedTables) {
        return new SecureQueryBuilder(allowedColumns, allowedTables);
    }

    /**
     * SELECT 절 추가
     */
    public SecureQueryBuilder select(String... columns) {
        queryBuffer.append("SELECT ");
        appendColumns(columns);
        return this;
    }

    /**
     * FROM 절 추가
     */
    public SecureQueryBuilder from(String tableName) {
        validateTableName(tableName);
        queryBuffer.append(" FROM ").append(sanitizeIdentifier(tableName));
        return this;
    }

    /**
     * WHERE 절 시작
     */
    public SecureQueryBuilder where() {
        queryBuffer.append(" WHERE 1=1");
        return this;
    }

    /**
     * AND 조건 추가 (파라미터 바인딩)
     */
    public SecureQueryBuilder andEquals(String column, Object value) {
        if (value == null) {
            return this;
        }
        validateColumnName(column);
        queryBuffer.append(" AND ").append(sanitizeIdentifier(column)).append(" = ?");
        parameterList.add(value);
        return this;
    }

    /**
     * AND LIKE 조건 추가
     */
    public SecureQueryBuilder andLike(String column, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return this;
        }
        validateColumnName(column);
        queryBuffer.append(" AND ").append(sanitizeIdentifier(column)).append(" LIKE ?");
        parameterList.add(sanitizeLikePattern(pattern));
        return this;
    }

    /**
     * AND IN 조건 추가
     */
    public SecureQueryBuilder andIn(String column, List<?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        validateColumnName(column);
        queryBuffer.append(" AND ").append(sanitizeIdentifier(column)).append(" IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                queryBuffer.append(", ");
            }
            queryBuffer.append("?");
            parameterList.add(values.get(i));
        }
        queryBuffer.append(")");
        return this;
    }

    /**
     * AND IS NULL 조건 추가
     */
    public SecureQueryBuilder andIsNull(String column) {
        validateColumnName(column);
        queryBuffer.append(" AND (").append(sanitizeIdentifier(column)).append(" IS NULL OR ")
                .append(sanitizeIdentifier(column)).append(" = '')");
        return this;
    }

    /**
     * AND IS NOT NULL 조건 추가
     */
    public SecureQueryBuilder andIsNotNull(String column) {
        validateColumnName(column);
        queryBuffer.append(" AND ").append(sanitizeIdentifier(column)).append(" IS NOT NULL AND ")
                .append(sanitizeIdentifier(column)).append(" <> ''");
        return this;
    }

    /**
     * AND BETWEEN 조건 추가
     */
    public SecureQueryBuilder andBetween(String column, Object from, Object to) {
        if (from == null || to == null) {
            return this;
        }
        validateColumnName(column);
        queryBuffer.append(" AND ").append(sanitizeIdentifier(column)).append(" BETWEEN ? AND ?");
        parameterList.add(from);
        parameterList.add(to);
        return this;
    }

    /**
     * ORDER BY 절 추가
     */
    public SecureQueryBuilder orderBy(String column, SortDirection direction) {
        validateColumnName(column);
        queryBuffer.append(" ORDER BY ").append(sanitizeIdentifier(column))
                .append(" ").append(direction.name());
        return this;
    }

    /**
     * LIMIT 절 추가
     */
    public SecureQueryBuilder limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        queryBuffer.append(" LIMIT ?");
        parameterList.add(limit);
        return this;
    }

    /**
     * OFFSET 절 추가
     */
    public SecureQueryBuilder offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
        queryBuffer.append(" OFFSET ?");
        parameterList.add(offset);
        return this;
    }

    /**
     * 원시 SQL 조각 추가 (위험 패턴 검사 수행)
     */
    public SecureQueryBuilder appendRaw(String sqlFragment) {
        if (containsDangerousPattern(sqlFragment)) {
            throw new SqlInjectionAttemptException("Dangerous SQL pattern detected: " + maskSensitive(sqlFragment));
        }
        queryBuffer.append(sqlFragment);
        return this;
    }

    /**
     * 빌드된 쿼리 문자열 반환
     */
    public String build() {
        return queryBuffer.toString();
    }

    /**
     * 바인딩 파라미터 배열 반환
     */
    public Object[] getParameters() {
        return parameterList.toArray();
    }

    /**
     * 바인딩 파라미터 리스트 반환
     */
    public List<Object> getParameterList() {
        return Collections.unmodifiableList(parameterList);
    }

    /**
     * 쿼리와 파라미터를 포함한 결과 객체 반환
     */
    public QueryResult toResult() {
        return new QueryResult(build(), getParameters());
    }

    private void appendColumns(String... columns) {
        if (columns == null || columns.length == 0) {
            queryBuffer.append("*");
            return;
        }
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                queryBuffer.append(", ");
            }
            validateColumnName(columns[i]);
            queryBuffer.append(sanitizeIdentifier(columns[i]));
        }
    }

    private void validateColumnName(String column) {
        Objects.requireNonNull(column, "Column name cannot be null");
        if (!IDENTIFIER_PATTERN.matcher(column).matches()) {
            throw new InvalidIdentifierException("Invalid column name format: " + maskSensitive(column));
        }
        if (!allowedColumns.isEmpty() && !allowedColumns.contains(column.toLowerCase())) {
            throw new InvalidIdentifierException("Column not in whitelist: " + column);
        }
    }

    private void validateTableName(String table) {
        Objects.requireNonNull(table, "Table name cannot be null");
        if (!IDENTIFIER_PATTERN.matcher(table).matches()) {
            throw new InvalidIdentifierException("Invalid table name format: " + maskSensitive(table));
        }
        if (!allowedTables.isEmpty() && !allowedTables.contains(table.toLowerCase())) {
            throw new InvalidIdentifierException("Table not in whitelist: " + table);
        }
    }

    private String sanitizeIdentifier(String identifier) {
        return identifier.replaceAll("[^a-zA-Z0-9_]", "");
    }

    private String sanitizeLikePattern(String pattern) {
        // LIKE 패턴의 특수문자 이스케이프
        return pattern.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static boolean containsDangerousPattern(String input) {
        if (input == null) {
            return false;
        }
        return DANGEROUS_SQL_PATTERN.matcher(input).find();
    }

    private static String maskSensitive(String input) {
        if (input == null || input.length() <= 10) {
            return "***";
        }
        return input.substring(0, 5) + "..." + input.substring(input.length() - 3);
    }

    /**
     * 정렬 방향
     */
    public enum SortDirection {
        ASC, DESC
    }

    /**
     * 쿼리 결과 레코드
     */
    public record QueryResult(String sql, Object[] parameters) {
    }

    /**
     * SQL Injection 시도 탐지 예외
     */
    public static class SqlInjectionAttemptException extends SecurityException {
        public SqlInjectionAttemptException(String message) {
            super(message);
        }
    }

    /**
     * 유효하지 않은 식별자 예외
     */
    public static class InvalidIdentifierException extends IllegalArgumentException {
        public InvalidIdentifierException(String message) {
            super(message);
        }
    }
}
