package kr.polytech.lms.haksa.repository;

import kr.polytech.lms.haksa.entity.LmsMemberView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 왜: COM.LMS_MEMBER_VIEW (22컬럼) 읽기 전용 리포지토리입니다.
 */
public interface LmsMemberViewRepository extends JpaRepository<LmsMemberView, String> {

    @Query("SELECT COUNT(m) FROM LmsMemberView m")
    long countAll();
}
