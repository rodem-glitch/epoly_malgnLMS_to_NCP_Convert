#!/usr/bin/env python3
"""
학사 미러 동기화 배치 스크립트
왜 필요한가:
- /main/poly_sync.jsp 는 로컬 호출만 허용하므로 서버 내부에서 주기 실행이 필요합니다.
- 결과 코드(rst_code)를 검사해 실패를 즉시 감지해야 운영 누락을 줄일 수 있습니다.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path


def now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def log(message: str, log_file: str | None = None) -> None:
    line = f"[{now_text()}] {message}"
    print(line, flush=True)
    if log_file:
        path = Path(log_file)
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8") as fp:
            fp.write(line + "\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="서버 로컬에서 /main/poly_sync.jsp를 호출해 학사 미러를 동기화합니다."
    )
    parser.add_argument("--base-url", default="http://127.0.0.1", help="호출 대상 베이스 URL")
    parser.add_argument("--endpoint", default="/main/poly_sync.jsp", help="동기화 JSP 경로")
    parser.add_argument("--connect-timeout", type=float, default=10.0, help="연결 타임아웃(초)")
    parser.add_argument("--read-timeout", type=float, default=300.0, help="응답 타임아웃(초)")
    parser.add_argument("--log-file", default=None, help="로그 파일 경로(선택)")
    parser.add_argument("--save-response-json", default=None, help="원본 응답 JSON 저장 경로(선택)")

    parser.add_argument("--mode", default=None, help="poly_sync.jsp mode 파라미터(예: student_only)")
    parser.add_argument("--member-cnt", type=int, default=None)
    parser.add_argument("--student-cnt", type=int, default=None)
    parser.add_argument("--course-cnt", type=int, default=None)
    parser.add_argument("--professor-cnt", type=int, default=None)
    parser.add_argument("--require-year", type=int, default=None)
    parser.add_argument("--start-year", type=int, default=None)
    parser.add_argument("--end-year", type=int, default=None)
    parser.add_argument("--dry-run-user-delete", default=None, help="Y/N")
    parser.add_argument("--exclude-login-ids", default=None, help="자동삭제 제외 계정 목록(| 또는 , 구분)")
    parser.add_argument("--min-member-ratio", type=int, default=None, help="회원 스냅샷 최소 비율(%%)")
    return parser


def build_payload(args: argparse.Namespace) -> dict[str, str]:
    mapping = {
        "mode": args.mode,
        "member_cnt": args.member_cnt,
        "student_cnt": args.student_cnt,
        "course_cnt": args.course_cnt,
        "professor_cnt": args.professor_cnt,
        "require_year": args.require_year,
        "start_year": args.start_year,
        "end_year": args.end_year,
        "dry_run_user_delete": args.dry_run_user_delete,
        "exclude_login_ids": args.exclude_login_ids,
        "min_member_ratio": args.min_member_ratio,
    }
    payload: dict[str, str] = {}
    for key, value in mapping.items():
        if value is None:
            continue
        payload[key] = str(value)
    return payload


def request_sync(url: str, payload: dict[str, str], timeout: float) -> str:
    data = urllib.parse.urlencode(payload).encode("utf-8")
    request = urllib.request.Request(
        url=url,
        data=data,
        method="POST",
        headers={"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"},
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw = response.read()
    return raw.decode("utf-8", errors="replace").strip()


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    endpoint = args.endpoint if args.endpoint.startswith("/") else f"/{args.endpoint}"
    url = f"{base_url}{endpoint}"
    timeout = args.connect_timeout + args.read_timeout
    payload = build_payload(args)

    log(f"poly_sync 배치 시작: url={url}", args.log_file)
    if payload:
        log(f"요청 파라미터: {payload}", args.log_file)
    else:
        log("요청 파라미터: 없음(서버 기본값 사용)", args.log_file)

    try:
        body = request_sync(url=url, payload=payload, timeout=timeout)
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        log(f"HTTP 오류: status={e.code}, body={detail[:500]}", args.log_file)
        return 10
    except urllib.error.URLError as e:
        log(f"접속 오류: {e}", args.log_file)
        return 11
    except Exception as e:
        log(f"알 수 없는 호출 오류: {e}", args.log_file)
        return 12

    if args.save_response_json:
        output_path = Path(args.save_response_json)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(body, encoding="utf-8")

    try:
        result = json.loads(body)
    except json.JSONDecodeError:
        log(f"JSON 파싱 실패: {body[:500]}", args.log_file)
        return 20

    rst_code = str(result.get("rst_code", ""))
    rst_message = str(result.get("rst_message", ""))
    log(f"응답 코드: rst_code={rst_code}, rst_message={rst_message}", args.log_file)

    if rst_code != "0000":
        log(f"동기화 실패 응답: {json.dumps(result, ensure_ascii=False)}", args.log_file)
        return 30

    log(f"동기화 성공: {json.dumps(result, ensure_ascii=False)}", args.log_file)
    return 0


if __name__ == "__main__":
    sys.exit(main())
