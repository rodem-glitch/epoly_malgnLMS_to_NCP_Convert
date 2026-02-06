package kr.go.growailms.recocontent.repository;

import java.util.List;
import java.util.Optional;
import kr.go.growailms.recocontent.entity.RecoContent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoContentRepository extends JpaRepository<RecoContent, Long> {
    
    // shkim_contentsummary1 브랜치에서 추가한 메서드들
    boolean existsByLessonId(String lessonId);

    Optional<RecoContent> findTopByLessonIdOrderByIdDesc(String lessonId);

    // main 브랜치에서 추가한 메서드
    @Query(
        """
        select r
          from RecoContent r
         where r.title like concat('%', :q, '%')
            or r.keywords like concat('%', :q, '%')
            or r.summary like concat('%', :q, '%')
        """
    )
    List<RecoContent> searchByKeyword(@Param("q") String query, Pageable pageable);
}