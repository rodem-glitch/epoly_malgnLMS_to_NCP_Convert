services:
  mysql:
    image: mysql:8.4
    container_name: lms-mysql
    restart: unless-stopped
    command:
      [
        "--character-set-server=utf8mb4",
        "--collation-server=utf8mb4_unicode_ci",
        "--lower_case_table_names=1",
      ]
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u${MYSQL_USER}", "-p${MYSQL_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 20
    volumes:
      - mysql_data:/var/lib/mysql
    networks:
      - lms_net

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
      mysql:
        condition: service_healthy
      qdrant:
        condition: service_started
    env_file:
      - .env
    environment:
      DB_URL: ${APP_DB_URL}
      DB_USERNAME: ${MYSQL_USER}
      DB_PASSWORD: ${MYSQL_PASSWORD}
      # 왜: JAR 안에 남아있는 application-local.yml 값보다 운영 환경변수를 우선 적용해
      # 잘못된 로컬 DB(예: 사설망 IP)로 붙는 사고를 막습니다.
      SPRING_DATASOURCE_URL: ${APP_DB_URL}
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      QDRANT_HOST: qdrant
      QDRANT_GRPC_PORT: 6334
      QDRANT_API_KEY: ${QDRANT_API_KEY}
      QDRANT_COLLECTION: ${QDRANT_COLLECTION}
      QDRANT_USE_TLS: "false"
      # 왜: 로컬 설정 파일이 qdrant=localhost를 덮어쓰지 못하게 Spring 최종 속성으로 고정합니다.
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
    ports:
      - "127.0.0.1:8081:8081"
    networks:
      - lms_net

  resin:
    image: expertsystems/resin:latest
    container_name: lms-resin
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      # 왜: JSP 추천 엔드포인트가 내부 Spring API를 항상 같은 서비스명으로 바라보게 고정합니다.
      POLYTECH_LMS_API_BASE: http://api:8081
    # 왜: 레거시 JSP/DAO 구조(public_html)를 그대로 실행해
    # 학생 로그인/신규메인/교수자 진입의 세션 흐름을 유지합니다.
    volumes:
      - ./legacy/public_html:/var/resin/webapps/ROOT
      - ./legacy/src:/opt/polytech-lms/legacy/src:ro
    ports:
      - "127.0.0.1:8080:8080"
    networks:
      - lms_net

volumes:
  mysql_data:
  qdrant_data:

networks:
  lms_net:
    driver: bridge
