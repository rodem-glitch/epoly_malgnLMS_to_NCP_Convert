package kr.go.growailms.contentsummary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "TB_KOLLUS_TRANSCRIPT")
public class KollusTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false)
    private Integer siteId;

    @Column(name = "channel_key", nullable = false, length = 100)
    private String channelKey;

    @Column(name = "media_content_key", nullable = false, length = 100)
    private String mediaContentKey;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "transcript_text", columnDefinition = "LONGTEXT")
    private String transcriptText;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "transcribed_at")
    private LocalDateTime transcribedAt;

    protected KollusTranscript() {
        // 왜: JPA는 리플렉션으로 엔티티를 생성하므로, 기본 생성자가 필요합니다.
    }

    public KollusTranscript(Integer siteId, String channelKey, String mediaContentKey) {
        // 왜: (site_id, media_content_key) 유니크로 잡아 "같은 영상 중복 처리"를 막습니다.
        this.siteId = siteId;
        this.channelKey = channelKey;
        this.mediaContentKey = mediaContentKey;
        this.status = "PENDING";
        this.retryCount = 0;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) status = "PENDING";
        if (retryCount == null) retryCount = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Integer getSiteId() {
        return siteId;
    }

    public String getChannelKey() {
        return channelKey;
    }

    public String getMediaContentKey() {
        return mediaContentKey;
    }

    public String getTitle() {
        return title;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getTranscriptText() {
        return transcriptText;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getTranscribedAt() {
        return transcribedAt;
    }

    public void markProcessing(String title) {
        this.status = "PROCESSING";
        this.title = title;
        this.lastError = null;
    }

    public void markPending(String title) {
        // 왜: webhook/백필에서는 "일단 대기열에 넣기"가 목적이라 PENDING 상태로 저장합니다.
        // - 이미 DONE이면 재처리하면 비용이 들 수 있으니, 호출하는 쪽에서 먼저 걸러줍니다.
        this.status = "PENDING";
        if (title != null && !title.isBlank()) this.title = title;
        this.lastError = null;
    }

    public void markDone(String transcriptText) {
        // 왜: 기존 코드와의 호환을 위해 남겨두되, 실제 의미는 "전사 완료(요약 대기)"입니다.
        markTranscribed(transcriptText);
    }

    public void markTranscribed(String transcriptText) {
        // 왜: 요약 단계가 추가되면서, 전사 완료와 "최종 완료(DONE)"를 분리해야 재시도 비용(재전사)을 줄일 수 있습니다.
        this.status = "TRANSCRIBED";
        this.transcriptText = transcriptText;
        this.transcribedAt = LocalDateTime.now();
        this.lastError = null;
        this.retryCount = 0;
    }

    public void markSummaryDone() {
        // 왜: 요약을 TB_RECO_CONTENT에 저장까지 끝났을 때 최종 완료로 표시합니다.
        this.status = "DONE";
        this.lastError = null;
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        this.lastError = errorMessage;
    }

    public void markSummaryFailed(String errorMessage) {
        this.status = "SUMMARY_FAILED";
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        this.lastError = errorMessage;
    }
}
