# 학사 미러(viewtable) 동기화 배치

## 왜 필요한가
- `public_html/main/poly_sync.jsp`는 로컬 IP에서만 호출되도록 막혀 있어, 서버 내부 배치가 필요합니다.
- 배치에서 `rst_code`를 확인해야 실패를 놓치지 않습니다.

## 파일 구성
- `tools/poly_sync/run_poly_sync.py`
  - 실제 POST 호출/응답검사 담당
  - `rst_code != 0000`이면 종료코드 `30`으로 실패 처리
- `tools/poly_sync/run_poly_sync.sh`
  - 서버 실행용 래퍼
  - 환경변수로 URL/로그 경로를 주입 가능

## 수동 실행
```bash
cd /opt/polytech-lms
chmod +x tools/poly_sync/run_poly_sync.sh
tools/poly_sync/run_poly_sync.sh --start-year 2025 --end-year 2026
```

## 자주 쓰는 옵션
- `--mode student_only`
- `--start-year 2025 --end-year 2026`
- `--student-cnt 200000`
- `--dry-run-user-delete Y`

## 자동 실행(cron) 예시
```bash
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# 매일 02:10 전체 동기화
10 2 * * * cd /opt/polytech-lms && POLY_SYNC_LOG_FILE=/var/log/malgnlms/poly_sync.log tools/poly_sync/run_poly_sync.sh >> /var/log/malgnlms/poly_sync_cron.log 2>&1

# 평일 03:10 수강생만 동기화(최근 2년)
10 3 * * 1-5 cd /opt/polytech-lms && POLY_SYNC_LOG_FILE=/var/log/malgnlms/poly_sync_student.log tools/poly_sync/run_poly_sync.sh --mode student_only --start-year 2025 --end-year 2026 >> /var/log/malgnlms/poly_sync_student_cron.log 2>&1
```

## 운영 체크 포인트
- 동시 실행 충돌을 막기 위해 cron에서는 `flock` 사용을 권장합니다.
  - 예: `flock -n /tmp/poly_sync.lock tools/poly_sync/run_poly_sync.sh ...`
- 실패 알림 기준은 종료코드 또는 로그의 `rst_code != 0000`으로 잡아주세요.
