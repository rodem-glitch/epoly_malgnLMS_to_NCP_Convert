# NCP 학사 연동 운영 가이드

## 1. 아키텍처

```
[e-poly API] ──HTTP──> [poly_sync.jsp (Resin:8080)]
                              │
                         UPSERT/SWAP
                              │
                     [LM_POLY_* 미러 테이블]
                              │
                         SELECT/JOIN
                              │
                  [학사 JSP/React UI (교수자 화면)]
```

**데이터 흐름** (단방향: 외부 → LMS):
1. `run_poly_sync.py`가 `poly_sync.jsp`를 HTTP로 호출
2. `poly_sync.jsp`가 e-poly API에서 과목/회원/수강생 데이터를 조회
3. 조회 결과를 `LM_POLY_*` 미러 테이블에 UPSERT (임시 테이블 → RENAME SWAP)
4. 교수자 화면(강의목차, 수강생, 출석, 성적)이 미러 테이블을 조회하여 표시

## 2. 초기 설정

### 2-1. 자동 배포 (CI/CD 또는 Pull 배포)

`deploy-was.sh`가 배포 과정에서 자동으로:
- 학사 DDL 적용 (`CREATE TABLE IF NOT EXISTS`)
- cron 등록 (전체 + 수강생 동기화)

### 2-2. 수동 초기화 (원스탑)

배포 후 추가 초기화가 필요하거나, 처음 세팅하는 경우:

```bash
# dry-run으로 먼저 확인
sudo bash /opt/polytech-lms/setup-haksa-sync.sh --dry-run

# 실제 실행
sudo bash /opt/polytech-lms/setup-haksa-sync.sh
```

`setup-haksa-sync.sh`가 수행하는 작업:
1. DDL 적용 (테이블 생성)
2. MyISAM → InnoDB 마이그레이션
3. SiteConfig 기본값 초기화 (`poly_auto_delete_yn=N`)
4. 첫 동기화 실행
5. cron 등록 확인
6. 결과 요약 출력

## 3. cron 스케줄

| 시간 | 모드 | 설명 |
|------|------|------|
| 매일 02:10 | 전체 동기화 | 과목 + 회원 + 수강생 + 교수 + 매핑 |
| 평일 03:10 | 수강생 빠른 동기화 | 수강생만 (student_only) |

cron 확인:
```bash
crontab -l | grep polytech-lms-haksa-sync
```

cron 제거 (필요 시):
```bash
crontab -l | grep -v polytech-lms-haksa-sync | crontab -
```

## 4. 수동 실행

### 전체 동기화
```bash
POLY_SYNC_BASE_URL=http://127.0.0.1:8080 \
POLY_SYNC_LOG_FILE=/var/log/malgnlms/poly_sync.log \
  /opt/polytech-lms/tools/poly_sync/run_poly_sync.sh \
  --start-year 2025 --end-year 2026
```

### 수강생만 동기화
```bash
POLY_SYNC_BASE_URL=http://127.0.0.1:8080 \
POLY_SYNC_LOG_FILE=/var/log/malgnlms/poly_sync_student.log \
  /opt/polytech-lms/tools/poly_sync/run_poly_sync.sh \
  --mode student_only --start-year 2025 --end-year 2026
```

### 옵션

| 옵션 | 설명 | 기본값 |
|------|------|--------|
| `--start-year` | 동기화 시작 연도 | (필수) |
| `--end-year` | 동기화 종료 연도 | (필수) |
| `--mode` | `full` 또는 `student_only` | `full` |
| `--save-response-json` | 응답을 JSON 파일로 저장 | 없음 |

### 환경변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `POLY_SYNC_BASE_URL` | Resin 접속 URL | `http://127.0.0.1:8080` |
| `POLY_SYNC_ENDPOINT` | JSP 엔드포인트 경로 | `/main/poly_sync.jsp` |
| `POLY_SYNC_LOG_FILE` | 로그 파일 경로 | 없음 (stdout) |
| `POLY_SYNC_PYTHON` | Python3 바이너리 경로 | `python3` |

## 5. 모니터링

### 로그 파일

| 파일 | 내용 |
|------|------|
| `/var/log/malgnlms/poly_sync.log` | 전체 동기화 상세 로그 |
| `/var/log/malgnlms/poly_sync_cron.log` | 전체 동기화 cron 실행 로그 |
| `/var/log/malgnlms/poly_sync_student.log` | 수강생 동기화 상세 로그 |
| `/var/log/malgnlms/poly_sync_student_cron.log` | 수강생 동기화 cron 실행 로그 |

### 성공 판단

응답 JSON에서:
- `rst_code=0000` → 성공
- `rst_course_saved > 0` → 과목 동기화 완료
- `rst_member_saved > 0` → 회원 동기화 완료

### DB 확인

```sql
-- 미러 테이블 존재 확인
SHOW TABLES LIKE 'LM_POLY_%';

-- 데이터 건수 확인
SELECT COUNT(*) FROM LM_POLY_COURSE;
SELECT COUNT(*) FROM LM_POLY_STUDENT;
SELECT COUNT(*) FROM LM_POLY_MEMBER;

-- 마지막 동기화 시각 확인
SELECT * FROM LM_POLY_SYNC_LOG;
```

## 6. 트러블슈팅

### 동기화 실행 안 됨

| 증상 | 원인 | 해결 |
|------|------|------|
| `python3: command not found` | Python3 미설치 | `apt-get install -y python3` |
| `Connection refused :8080` | Resin 미기동 | `docker ps`로 lms-resin 확인 후 `docker compose up -d` |
| `Table doesn't exist` | DDL 미적용 | `setup-haksa-sync.sh` 실행 |
| cron 미등록 | deploy-was.sh 미실행 | `deploy-was.sh` 재실행 또는 수동 cron 등록 |

### e-poly API 연결 실패

```bash
# Resin 컨테이너 안에서 e-poly API 접근 테스트
docker exec lms-resin curl -v https://e-poly.example.com/api/test
```

- VPN/네트워크 이슈 → 인프라팀에 확인 요청
- DNS 이슈 → `/etc/hosts` 또는 Docker DNS 설정 확인

### 데이터 불일치

```sql
-- 동기화 시간 비교
SELECT SYNC_DATE, COUNT(*) FROM LM_POLY_COURSE GROUP BY SYNC_DATE ORDER BY SYNC_DATE DESC LIMIT 5;

-- 특정 과목 원본 확인
SELECT COURSE_CODE, COURSE_NAME, SYNC_DATE FROM LM_POLY_COURSE WHERE OPEN_YEAR='2025' LIMIT 10;
```

수동으로 재동기화하면 최신 데이터로 덮어씁니다.

## 7. SiteConfig 설정

| 키 | 값 | 설명 |
|----|-----|------|
| `poly_auto_delete_yn` | `N` (기본) | 학사 과목 폐강 시 LMS 데이터 자동삭제 여부. `Y`로 변경 시 폐강 과목의 강의목차/성적이 자동 삭제됩니다. |

변경 방법:
```sql
UPDATE LM_SITE_CONFIG SET config_value='Y', mod_date=DATE_FORMAT(NOW(), '%Y%m%d%H%i%s')
WHERE config_key='poly_auto_delete_yn';
```
