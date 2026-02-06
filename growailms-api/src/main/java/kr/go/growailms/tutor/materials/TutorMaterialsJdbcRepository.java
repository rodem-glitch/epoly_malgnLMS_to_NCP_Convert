package kr.go.growailms.tutor.materials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 왜: materials_*.jsp 3개가 LibraryDao, CourseLibraryDao 등으로 하던 DB 접근을
 *     NamedParameterJdbcTemplate으로 대체합니다.
 *     테이블: LM_LIBRARY, LM_COURSE_LIBRARY, LM_COURSE_TUTOR
 */
@Repository
public class TutorMaterialsJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(TutorMaterialsJdbcRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public TutorMaterialsJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 권한 확인 ====================

    public boolean isMajorTutor(long userId, int courseId, long siteId) {
        String sql = """
                SELECT COUNT(*) FROM LM_COURSE_TUTOR
                WHERE course_id = :courseId AND user_id = :userId
                  AND type = 'major' AND site_id = :siteId AND status != -1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("userId", userId)
                .addValue("siteId", siteId);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    // ==================== materials_list.jsp ====================

    /**
     * 왜: 과목에 연결된 자료 목록을 조회합니다 (LM_COURSE_LIBRARY JOIN LM_LIBRARY).
     */
    public List<Map<String, Object>> listMaterials(int courseId, long siteId) {
        String sql = """
                SELECT l.id library_id, l.library_nm, l.content, l.library_file, l.library_link,
                       l.download_cnt, l.reg_date
                FROM LM_COURSE_LIBRARY cl
                INNER JOIN LM_LIBRARY l ON cl.library_id = l.id AND l.site_id = :siteId AND l.status != -1
                WHERE cl.course_id = :courseId AND cl.site_id = :siteId AND cl.status != -1
                ORDER BY cl.sort, l.id DESC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("siteId", siteId);
        return jdbc.queryForList(sql, params);
    }

    // ==================== materials_upload.jsp ====================

    /**
     * 왜: 자료(LM_LIBRARY)를 새로 생성합니다.
     */
    public long insertLibrary(long siteId, String libraryNm, String content,
                               String libraryLink, String libraryFile) {
        String sql = """
                INSERT INTO LM_LIBRARY (site_id, library_nm, content, library_link, library_file, status, reg_date)
                VALUES (:siteId, :libraryNm, :content, :libraryLink, :libraryFile, 1, NOW())
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("siteId", siteId)
                .addValue("libraryNm", libraryNm)
                .addValue("content", content)
                .addValue("libraryLink", libraryLink)
                .addValue("libraryFile", libraryFile);
        jdbc.update(sql, params, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0;
    }

    /**
     * 왜: 과목-자료 연결(LM_COURSE_LIBRARY)을 생성합니다.
     */
    public void linkCourseLibrary(int courseId, long libraryId, long siteId) {
        String sql = """
                INSERT INTO LM_COURSE_LIBRARY (course_id, library_id, site_id, status, reg_date)
                VALUES (:courseId, :libraryId, :siteId, 1, NOW())
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("libraryId", libraryId)
                .addValue("siteId", siteId);
        jdbc.update(sql, params);
    }

    // ==================== materials_delete.jsp ====================

    /**
     * 왜: 과목-자료 연결만 해제합니다 (자료 자체는 삭제하지 않음).
     */
    public int unlinkCourseLibrary(int courseId, int libraryId, long siteId) {
        String sql = """
                UPDATE LM_COURSE_LIBRARY SET status = -1
                WHERE course_id = :courseId AND library_id = :libraryId AND site_id = :siteId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("libraryId", libraryId)
                .addValue("siteId", siteId);
        return jdbc.update(sql, params);
    }
}
