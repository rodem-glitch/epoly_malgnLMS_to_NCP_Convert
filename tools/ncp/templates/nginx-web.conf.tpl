# 왜: WEB/WAS 분리 구조에서 Nginx가 WAS 서버(192.168.2.6)의 API/Resin 컨테이너로
# 요청을 분배합니다. GCP 단일 VM의 127.0.0.1 대신 WAS private IP를 사용합니다.

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

    # 왜: 통계/추천/채용 등 Spring Boot 전용 엔드포인트는 API 컨테이너로 보냅니다.
    location ^~ /statistics/ {
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
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location ^~ /livesession/ {
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
    }
    location ^~ /actuator/ {
        proxy_pass http://was_api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_redirect http://was_api/ /;
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
