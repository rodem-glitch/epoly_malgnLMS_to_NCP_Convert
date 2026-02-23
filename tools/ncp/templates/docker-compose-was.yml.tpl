# 왜: NCP WAS 서버 전용 Docker Compose. GCP 버전과 달리 MySQL 서비스가 없습니다.
# Cloud DB for MySQL(mysql 8.0.42)을 사용하므로 DB 컨테이너가 불필요합니다.
# 포트 바인딩이 0.0.0.0으로 변경되어 WEB 서버(192.168.1.6)에서 접근 가능합니다.

services:
  qdrant:
    image: qdrant/qdrant:v1.15.3
    container_name: lms-qdrant
    restart: unless-stopped
    # 왜: Qdrant가 정상 응답하는지 주기적으로 확인하여, 비정상 시 자동 재시작합니다.
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:6333/healthz"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 15s
    stop_grace_period: 30s
    environment:
      QDRANT__SERVICE__API_KEY: ${QDRANT_API_KEY}
      QDRANT__SERVICE__GRPC_PORT: 6334
      QDRANT__SERVICE__HTTP_PORT: 6333
      QDRANT__LOG_LEVEL: INFO
    volumes:
      - qdrant_data:/qdrant/storage
    # 왜: WAS 4Core/16GB 기준, 벡터 DB에 2GB/1코어를 할당합니다.
    deploy:
      resources:
        limits:
          memory: 2g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - lms_net

  api:
    image: eclipse-temurin:17-jre
    container_name: lms-api
    restart: unless-stopped
    depends_on:
      qdrant:
        # 왜: Qdrant가 healthy여야 임베딩 검색이 정상 동작합니다.
        condition: service_healthy
    # 왜: Spring Boot actuator health로 컨테이너 상태를 확인합니다.
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    stop_grace_period: 30s
    env_file:
      - .env
    environment:
      # 왜: Cloud DB 엔드포인트를 환경변수로 주입합니다. mysql 컨테이너 대신 NCP Cloud DB 사용.
      DB_URL: ${APP_DB_URL}
      DB_USERNAME: ${MYSQL_USER}
      DB_PASSWORD: ${MYSQL_PASSWORD}
      SPRING_DATASOURCE_URL: ${APP_DB_URL}
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      QDRANT_HOST: qdrant
      QDRANT_GRPC_PORT: 6334
      QDRANT_API_KEY: ${QDRANT_API_KEY}
      QDRANT_COLLECTION: ${QDRANT_COLLECTION}
      QDRANT_USE_TLS: "false"
      SPRING_AI_VECTORSTORE_QDRANT_HOST: qdrant
      SPRING_AI_VECTORSTORE_QDRANT_PORT: 6334
      SPRING_AI_VECTORSTORE_QDRANT_API_KEY: ${QDRANT_API_KEY}
      SPRING_AI_VECTORSTORE_QDRANT_COLLECTION_NAME: ${QDRANT_COLLECTION}
      SPRING_AI_VECTORSTORE_QDRANT_USE_TLS: "false"
      GOOGLE_API_KEY: ${GOOGLE_API_KEY}
      GEMINI_API_KEY: ${GEMINI_API_KEY}
      # 왜: 학사 Oracle DB(VPN 경유) 접속 정보를 Spring Boot에 전달합니다.
      HAKSA_ORACLE_URL: ${HAKSA_ORACLE_URL:-}
      HAKSA_ORACLE_USERNAME: ${HAKSA_ORACLE_USERNAME:-}
      HAKSA_ORACLE_PASSWORD: ${HAKSA_ORACLE_PASSWORD:-}
      SPRING_PROFILES_ACTIVE: prod
      SERVER_PORT: 8081
      CONTENTSUMMARY_WORKER_ENABLED: "false"
      # 왜: 통계 대시보드가 엑셀 원본 파일을 직접 읽는 구조라, 컨테이너 내부 고정 경로로 명시합니다.
      STATISTICS_MAJOR_INDUSTRY_FILE: /data/statistics/통계 기능 관련 학과 정보 매칭.xlsx
      STATISTICS_EMPLOYMENT_FILE: /data/statistics/2024.02_학위과정 졸업자 취업률_집계배포_251204.xlsx
      STATISTICS_ADMISSION_FILE: /data/statistics/입시율관리.xlsx
      STATISTICS_STUDENT_POPULATION_FILE: /data/statistics/재학생_인구_가데이터_20260120.xlsx
      # 왜: 컨테이너 메모리 제한(6g)에 맞춘 JVM 옵션. G1GC + UseContainerSupport로 안정성 확보.
      JAVA_TOOL_OPTIONS: "-Xms2g -Xmx4g -XX:+UseG1GC -XX:+UseContainerSupport"
    command: ["java", "-jar", "/app/polytech-lms-api.jar"]
    volumes:
      - ./app/polytech-lms-api.jar:/app/polytech-lms-api.jar:ro
      - ./statistics_data:/data/statistics:ro
      - ./logs/api:/app/logs
    # 왜: 0.0.0.0 바인딩으로 WEB 서버(192.168.1.6)에서 접근을 허용합니다.
    ports:
      - "0.0.0.0:8081:8081"
    # 왜: Spring Boot + AI + 통계에 6GB/2코어를 할당합니다.
    deploy:
      resources:
        limits:
          memory: 6g
          cpus: "2.0"
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - lms_net

  resin:
    image: expertsystems/resin:latest
    container_name: lms-resin
    restart: unless-stopped
    depends_on:
      api:
        # 왜: Resin의 추천 JSP가 API를 호출하므로, API가 healthy여야 합니다.
        condition: service_healthy
    # 왜: Resin 메인 페이지로 컨테이너 상태를 확인합니다.
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s
    stop_grace_period: 30s
    environment:
      # 왜: JSP 추천 엔드포인트가 내부 Spring API를 항상 같은 서비스명으로 바라보게 고정합니다.
      POLYTECH_LMS_API_BASE: http://api:8081
    volumes:
      - ./legacy/public_html:/var/resin/webapps/ROOT
      - ./legacy/src:/opt/polytech-lms/legacy/src:ro
      - ./logs/resin:/var/resin/log
    # 왜: 0.0.0.0 바인딩으로 WEB 서버(192.168.1.6)에서 접근을 허용합니다.
    ports:
      - "0.0.0.0:8080:8080"
    # 왜: 레거시 JSP + 파일 업로드에 4GB/2코어를 할당합니다.
    deploy:
      resources:
        limits:
          memory: 4g
          cpus: "2.0"
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - lms_net

volumes:
  qdrant_data:

networks:
  lms_net:
    driver: bridge
