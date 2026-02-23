# 왜: WEB/WAS 분리 구조에서 Nginx가 WAS 서버(192.168.2.6)의 API/Resin 컨테이너로
# 요청을 분배합니다. GCP 단일 VM의 127.0.0.1 대신 WAS private IP를 사용합니다.

# 왜: 로그인/API 엔드포인트별 요청 속도를 제한하여 무차별 대입·남용을 방지합니다.
limit_req_zone $binary_remote_addr zone=login_limit:10m rate=5r/s;
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=30r/s;

upstream was_api {
    server __WAS_IP__:8081;
}

upstream was_resin {
    server __WAS_IP__:8080;
}

server {
    listen 80;
    listen [::]:80;
    server_name __WEB_DOMAIN__ _;

    # 왜: 업로드/다운로드가 있는 LMS 특성상 기본 제한보다 크게 둡니다.
    client_max_body_size 100m;

    # ─── gzip 압축 ─────────────────────────────────────────────
    # 왜: 텍스트 응답을 60-80% 압축하여 전송량을 줄입니다.
    gzip on;
    gzip_comp_level 6;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/javascript application/json image/svg+xml;
    gzip_vary on;

    # ─── 보안 헤더 ─────────────────────────────────────────────
    # 왜: 클릭재킹·MIME 스니핑·XSS 등 일반적인 웹 공격을 브라우저 레벨에서 차단합니다.
    # CSP는 레거시 JSP 인라인 스크립트 호환 문제로 의도적 제외.
    add_header X-Frame-Options SAMEORIGIN always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    add_header X-XSS-Protection "1; mode=block" always;

    # ─── 정적 자산 캐시 ───────────────────────────────────────
    # 왜: 공용 리소스는 7일 캐시로 반복 다운로드를 줄입니다.
    location ~* ^/common/.*\.(css|js|png|jpg|jpeg|gif|ico|svg|woff2?|ttf|eot)$ {
        proxy_pass http://was_resin;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    # 왜: Vite 빌드 산출물은 파일명에 해시가 포함되어, 내용이 바뀌면 URL도 바뀝니다. 30일 캐시.
    location ~* ^/tutor_lms/app/assets/.*\.(css|js|png|jpg|jpeg|gif|ico|svg|woff2?|ttf|eot)$ {
        proxy_pass http://was_resin;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # ─── Actuator 접근 제한 ────────────────────────────────────
    # 왜: 운영 메트릭/헬스 정보는 내부 네트워크에서만 접근 가능하게 제한합니다.
    location ^~ /actuator/ {
        allow 192.168.0.0/16;
        allow 127.0.0.1;
        deny all;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }

    # 왜: 통계/추천/채용 등 Spring Boot 전용 엔드포인트는 API 컨테이너로 보냅니다.
    location ^~ /statistics/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location = /statistics {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location ^~ /job/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location ^~ /student/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location ^~ /tutor/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location ^~ /contentsummary/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location ^~ /reco-contents/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location ^~ /global/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location ^~ /antifraud/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    # 왜: 라이브 세션은 WebSocket을 사용할 수 있어 Upgrade/Connection 헤더가 필요합니다.
    location ^~ /livesession/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }

    # 왜: 로그인 엔드포인트는 무차별 대입 방지를 위해 더 엄격한 속도 제한을 적용합니다.
    location ~* ^/.*login.*\.jsp$ {
        limit_req zone=login_limit burst=10 nodelay;
        proxy_pass http://was_resin;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_resin/ /;
    }

    # 왜: 교수자 SPA 엔트리(index.html)가 레거시 응답 헤더에서 US-ASCII로 내려오면
    # 브라우저 탭 제목 한글이 깨질 수 있어 UTF-8을 강제합니다.
    location = /tutor_lms/app/index.html {
        proxy_pass http://was_resin;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_hide_header Content-Type;
        add_header Content-Type "text/html; charset=utf-8" always;
        proxy_redirect http://was_resin/ /;
    }

    # 왜: 그 외 모든 화면(JSP/템플릿/레거시 API)은 Resin이 처리합니다.
    location / {
        proxy_pass http://was_resin;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_resin/ /;
    }
}
