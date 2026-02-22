# 왜: NCP WAS 서버 전용 Docker Compose. GCP 버전과 달리 MySQL 서비스가 없습니다.
# Cloud DB for MySQL(mysql 8.0.42)을 사용하므로 DB 컨테이너가 불필요합니다.
# 포트 바인딩이 0.0.0.0으로 변경되어 WEB 서버(192.168.1.6)에서 접근 가능합니다.

services:
  qdrant:
    image: qdrant/qdrant:v1.15.3
    container_name: lms-qdrant
    restart: unless-stopped
    environment:
      QDRANT__SERVICE__API_KEY: ${QDRANT_API_KEY}
      QDRANT__SERVICE__GRPC_PORT: 6334
      QDRANT__SERVICE__HTTP_PORT: 6333
      QDRANT__LOG_LEVEL: INFO
    volumes:
      - qdrant_data:/qdrant/storage
    networks:
      - lms_net

  api:
    image: eclipse-temurin:17-jre
    container_name: lms-api
    restart: unless-stopped
    depends_on:
      qdrant:
        condition: service_started
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
      SPRING_PROFILES_ACTIVE: prod
      SERVER_PORT: 8081
      CONTENTSUMMARY_WORKER_ENABLED: "false"
      # 왜: 통계 대시보드가 엑셀 원본 파일을 직접 읽는 구조라, 컨테이너 내부 고정 경로로 명시합니다.
      STATISTICS_MAJOR_INDUSTRY_FILE: /data/statistics/통계 기능 관련 학과 정보 매칭.xlsx
      STATISTICS_EMPLOYMENT_FILE: /data/statistics/2024.02_학위과정 졸업자 취업률_집계배포_251204.xlsx
      STATISTICS_ADMISSION_FILE: /data/statistics/입시율관리.xlsx
      STATISTICS_STUDENT_POPULATION_FILE: /data/statistics/재학생_인구_가데이터_20260120.xlsx
    command: ["java", "-jar", "/app/polytech-lms-api.jar"]
    volumes:
      - ./app/polytech-lms-api.jar:/app/polytech-lms-api.jar:ro
      - ./statistics_data:/data/statistics:ro
    # 왜: 0.0.0.0 바인딩으로 WEB 서버(192.168.1.6)에서 접근을 허용합니다.
    ports:
      - "0.0.0.0:8081:8081"
    networks:
      - lms_net

  resin:
    image: expertsystems/resin:latest
    container_name: lms-resin
    restart: unless-stopped
    environment:
      # 왜: JSP 추천 엔드포인트가 내부 Spring API를 항상 같은 서비스명으로 바라보게 고정합니다.
      POLYTECH_LMS_API_BASE: http://api:8081
    volumes:
      - ./legacy/public_html:/var/resin/webapps/ROOT
      - ./legacy/src:/opt/polytech-lms/legacy/src:ro
    # 왜: 0.0.0.0 바인딩으로 WEB 서버(192.168.1.6)에서 접근을 허용합니다.
    ports:
      - "0.0.0.0:8080:8080"
    networks:
      - lms_net

volumes:
  qdrant_data:

networks:
  lms_net:
    driver: bridge
