package kr.go.growailms.contentsummary.repository;

import java.util.Optional;
import kr.go.growailms.contentsummary.entity.KollusTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentSummaryRepository extends JpaRepository<KollusTranscript, Long> {
    Optional<KollusTranscript> findBySiteIdAndMediaContentKey(Integer siteId, String mediaContentKey);
    
    java.util.List<KollusTranscript> findByDurationSecondsIsNull();
}
