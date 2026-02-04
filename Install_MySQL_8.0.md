rodem@cloudshell:~ (gen-lang-client-0725900816)$ # 로그 파일 생성 및 작업 스크립트 실행
cat > ~/mysql-setup.sh << 'EOF'
#!/bin/bash

# 로그 파일 설정
LOG_FILE="$HOME/mysql-setup-log-$(date +%Y%m%d_%H%M%S).txt"
INSTANCE_NAME="mysql-dev"
DB_NAME="myapp_db"
ROOT_PASSWORD="MySecurePassword123!"
APP_PASSWORD="AppPassword123!"

# 로그 함수
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log_cmd() {
    echo "" | tee -a "$LOG_FILE"
    log "=========================================="
    log "명령어: $1"
    log "=========================================="
}

# 작업 시작
log "=========================================="
log "MySQL Cloud SQL 설치 작업 시작"
log "프로젝트: $(gcloud config get-value project)"
log "=========================================="

# 1단계: API 활성화
log_cmd "Cloud SQL Admin API 활성화"
if gcloud services enable sqladmin.googleapis.com 2>&1 | tee -a "$LOG_FILE"; then
    log "✓ 성공: Cloud SQL Admin API 활성화 완료"
else
    log "✗ 오류: API 활성화 실패"
    exit 1
fi

# API 활성화 확인
log_cmd "활성화된 API 확인"
gcloud services list --enabled | grep sqladmin 2>&1 | tee -a "$LOG_FILE"

# 2단계: MySQL 인스턴스 생성
log_cmd "MySQL 인스턴스 생성 (5-10분 소요)"
if gcloud sql instances create $INSTANCE_NAME \
  --database-version=MYSQL_8_0 \
  --tier=db-n1-standard-1 \
  --region=asia-northeast3 \
  --storage-type=SSD \
  --storage-size=20GB \
  --backup-start-time=03:00 2>&1 | tee -a "$LOG_FILE"; then
    log "✓ 성공: MySQL 인스턴스 생성 완료"
else
    log "✗ 오류: MySQL 인스턴스 생성 실패"
    exit 1
fi

# 3단계: root 비밀번호 설정
log_cmd "root 비밀번호 설정"
if gcloud sql users set-password root \
  --host=% \
  --instance=$INSTANCE_NAME \
  --password="$ROOT_PASSWORD" 2>&1 | tee -a "$LOG_FILE"; then
~/mysql-setup.sh-setup.sh 습니다: $LOG_FILE"==="root">&1 | tee -a "$LOG_FILE"e(connectionName)")ess)")
[2026-02-04 17:32:59] ==========================================
[2026-02-04 17:32:59] MySQL Cloud SQL 설치 작업 시작
Your active configuration is: [cloudshell-15155]
[2026-02-04 17:33:01] 프로젝트: gen-lang-client-0725900816
[2026-02-04 17:33:01] ==========================================

[2026-02-04 17:33:01] ==========================================
[2026-02-04 17:33:01] 명령어: Cloud SQL Admin API 활성화
[2026-02-04 17:33:01] ==========================================
Operation "operations/acat.p2-212772069233-e2d9e460-79a5-491a-a862-3c7520b02364" finished successfully.
[2026-02-04 17:33:08] ✓ 성공: Cloud SQL Admin API 활성화 완료

[2026-02-04 17:33:08] ==========================================
[2026-02-04 17:33:08] 명령어: 활성화된 API 확인
[2026-02-04 17:33:08] ==========================================
NAME: sqladmin.googleapis.com

[2026-02-04 17:33:11] ==========================================
[2026-02-04 17:33:11] 명령어: MySQL 인스턴스 생성 (5-10분 소요)
[2026-02-04 17:33:11] ==========================================
Creating Cloud SQL instance for MYSQL_8_0...
...............done.
Created [https://sqladmin.googleapis.com/sql/v1beta4/projects/gen-lang-client-0725900816/instances/mysql-dev].
NAME: mysql-dev
DATABASE_VERSION: MYSQL_8_0
LOCATION: asia-northeast3-a
TIER: db-n1-standard-1
PRIMARY_ADDRESS: 34.22.87.246
PRIVATE_ADDRESS: -
STATUS: RUNNABLE
[2026-02-04 17:37:14] ✓ 성공: MySQL 인스턴스 생성 완료

[2026-02-04 17:37:14] ==========================================
[2026-02-04 17:37:14] 명령어: root 비밀번호 설정
[2026-02-04 17:37:14] ==========================================
Updating Cloud SQL user...
.done.
[2026-02-04 17:37:17] ✓ 성공: root 비밀번호 설정 완료

[2026-02-04 17:37:17] ==========================================
[2026-02-04 17:37:17] 명령어: 데이터베이스 생성
[2026-02-04 17:37:17] ==========================================
Creating Cloud SQL database...
.done.
Created database [myapp_db].
charset: utf8mb4
collation: utf8mb4_unicode_ci
instance: mysql-dev
name: myapp_db
project: gen-lang-client-0725900816
[2026-02-04 17:37:20] ✓ 성공: 데이터베이스 생성 완료

[2026-02-04 17:37:20] ==========================================
[2026-02-04 17:37:20] 명령어: 애플리케이션 사용자 생성
[2026-02-04 17:37:20] ==========================================
Creating Cloud SQL user...
.done.
Created user [appuser].
[2026-02-04 17:37:24] ✓ 성공: 애플리케이션 사용자 생성 완료

[2026-02-04 17:37:24] ==========================================
[2026-02-04 17:37:24] 명령어: 인스턴스 상세 정보
[2026-02-04 17:37:24] ==========================================
backendType: SECOND_GEN
connectionName: gen-lang-client-0725900816:asia-northeast3:mysql-dev
createTime: '2026-02-04T17:33:14.203Z'
databaseInstalledVersion: MYSQL_8_0_43
databaseVersion: MYSQL_8_0
etag: 5dae6496d0978002282f580e36f341689af0d171997340c2e5c57194beaba931
gceZone: asia-northeast3-a
geminiConfig:
  activeQueryEnabled: false
  entitled: false
  flagRecommenderEnabled: true
  indexAdvisorEnabled: false
includeReplicasForMajorVersionUpgrade: false
instanceType: CLOUD_SQL_INSTANCE
ipAddresses:
- ipAddress: 34.22.87.246
  type: PRIMARY
kind: sql#instance
maintenanceVersion: MYSQL_8_0_43.R20260117.02_02
name: mysql-dev
project: gen-lang-client-0725900816
region: asia-northeast3
satisfiesPzi: true
selfLink: https://sqladmin.googleapis.com/sql/v1beta4/projects/gen-lang-client-0725900816/instances/mysql-dev
serverCaCert:
  cert: |-
    -----BEGIN CERTIFICATE-----
    MIIDfzCCAmegAwIBAgIBADANBgkqhkiG9w0BAQsFADB3MS0wKwYDVQQuEyRkNzI0
    Y2JmMi02NzczLTRlMzctYjY3Yy1jYzBiYTU0Nzg0ZWMxIzAhBgNVBAMTGkdvb2ds
    ZSBDbG91ZCBTUUwgU2VydmVyIENBMRQwEgYDVQQKEwtHb29nbGUsIEluYzELMAkG
    A1UEBhMCVVMwHhcNMjYwMjA0MTczMzU5WhcNMzYwMjAyMTczNDU5WjB3MS0wKwYD
    VQQuEyRkNzI0Y2JmMi02NzczLTRlMzctYjY3Yy1jYzBiYTU0Nzg0ZWMxIzAhBgNV
    BAMTGkdvb2dsZSBDbG91ZCBTUUwgU2VydmVyIENBMRQwEgYDVQQKEwtHb29nbGUs
    IEluYzELMAkGA1UEBhMCVVMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIB
    AQCyqq3vc26PsPJfTI4/d5JgV6wTLT+GsCg2M2w37dhs1fwLHRmCOznlP4bV3RBO
    9rQEiV7EQ3SjGqm9sXcNjFcCZlrwot2bpTN7UHIyx+dBJ6MFZ6FEjnZyN3YaK6SV
    uGqPlh5+9b+pad/vaOp2MOoLxJdeYBcvaaz6+4jNajakC3B8+Ol+M4gEU4nWHxr6
    2CpXTI3++We0kyPKwO0oWWynIt6rhaALGSD/DfcqCLtb6YTTBhp6vcxEDNFAm483
    MyhsJHqIW88m1apV84heMwOEHEDOIgglHxb4smgKvlzUSEWAQWKnbdTU8FoZ2RmU
    DNlKUUtc8HuDdsdQv9mkR9GJAgMBAAGjFjAUMBIGA1UdEwEB/wQIMAYBAf8CAQAw
    DQYJKoZIhvcNAQELBQADggEBAGQLrc0Ik84lWTZo++ZWBIEq41FrSKWSWNvfzQl2
    11vQRC3t5nmHBZm1wD7Q8uqItES6UuTObZz6XJvSnYZhx+Yh1sbbhCZPSobiGiuw
    eDN7995N/luC/7Z2j+7PfrNMCvP2xaJjp9oDuuwYSOs/ziHzamnqzDrOQ2M0UH+j
    +d4ffIQYMcXWFH4RNPZMgD80mqlfALLyDTI7p4lBX0nfU57jsXvn+Pui7QhrG6D8
    bodt5JSxGsmmPhyP6C25/9yjdH9QDq3aaW0J7KsiNC1AuEv1A4ChZ2g1sIR7Y5XV
    x0X4OAYpefrEjTXCzIMOJdHQyGua29+UsbEaPtm7Z9PA/vo=
    -----END CERTIFICATE-----
  certSerialNumber: '0'
  commonName: C=US,O=Google\, Inc,CN=Google Cloud SQL Server CA,dnQualifier=d724cbf2-6773-4e37-b67c-cc0ba54784ec
  createTime: '2026-02-04T17:33:59.402Z'
  expirationTime: '2036-02-02T17:34:59.402Z'
  instance: mysql-dev
  kind: sql#sslCert
  sha1Fingerprint: ca42c5fe2d5c7fa97d40d980467beeb2404d4586
serviceAccountEmailAddress: p212772069233-fy7qws@gcp-sa-cloud-sql.iam.gserviceaccount.com
settings:
  activationPolicy: ALWAYS
  availabilityType: ZONAL
  backupConfiguration:
    backupRetentionSettings:
      retainedBackups: 7
      retentionUnit: COUNT
    backupTier: STANDARD
    enabled: true
    kind: sql#backupConfiguration
    startTime: 03:00
    transactionLogRetentionDays: 7
    transactionalLogStorageState: TRANSACTIONAL_LOG_STORAGE_STATE_UNSPECIFIED
  connectorEnforcement: NOT_REQUIRED
  dataDiskSizeGb: '20'
  dataDiskType: PD_SSD
  deletionProtectionEnabled: false
  edition: ENTERPRISE
  ipConfiguration:
    ipv4Enabled: true
    message: Configuring authorized network or using CloudSQL auth proxy or language
      connectors is a prerequisite for connecting to Public IP. Please refer to the
      documentation for more details https://cloud.google.com/sql/docs/mysql/authorize-networks.
    requireSsl: false
    serverCaMode: GOOGLE_MANAGED_INTERNAL_CA
    serverCertificateRotationMode: SERVER_CERTIFICATE_ROTATION_MODE_UNSPECIFIED
    sslMode: ALLOW_UNENCRYPTED_AND_ENCRYPTED
  kind: sql#settings
  locationPreference:
    kind: sql#locationPreference
    zone: asia-northeast3-a
  pricingPlan: PER_USE
  replicationLagMaxSeconds: 31536000
  replicationType: SYNCHRONOUS
  settingsVersion: '1'
  storageAutoResize: true
  storageAutoResizeLimit: '0'
  tier: db-n1-standard-1
sqlNetworkArchitecture: NEW_NETWORK_ARCHITECTURE
state: RUNNABLE
upgradableDatabaseVersions:
- displayName: MySQL 8.0.18
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_18
- displayName: MySQL 8.0.26
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_26
- displayName: MySQL 8.0.27
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_27
- displayName: MySQL 8.0.28
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_28
- displayName: MySQL 8.0.29
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_29
- displayName: MySQL 8.0.30
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_30
- displayName: MySQL 8.0.31
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_31
- displayName: MySQL 8.0.32
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_32
- displayName: MySQL 8.0.33
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_33
- displayName: MySQL 8.0.34
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_34
- displayName: MySQL 8.0.35
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_35
- displayName: MySQL 8.0.36
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_36
- displayName: MySQL 8.0.37
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_37
- displayName: MySQL 8.0.39
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_39
- displayName: MySQL 8.0.40
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_40
- displayName: MySQL 8.0.41
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_41
- displayName: MySQL 8.0.42
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_42
- displayName: MySQL 8.0.44
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_44
- displayName: MySQL 8.4
  majorVersion: MYSQL_8_4
  name: MYSQL_8_4

[2026-02-04 17:37:26] ==========================================
[2026-02-04 17:37:26] 명령어: 인스턴스 IP 주소
[2026-02-04 17:37:26] ==========================================
IP 주소: 34.22.87.246

[2026-02-04 17:37:27] ==========================================
[2026-02-04 17:37:27] 명령어: 인스턴스 연결 이름
[2026-02-04 17:37:27] ==========================================
연결 이름: gen-lang-client-0725900816:asia-northeast3:mysql-dev

[2026-02-04 17:37:29] ==========================================
[2026-02-04 17:37:29] 명령어: MySQL 사용자 목록
[2026-02-04 17:37:29] ==========================================
NAME: appuser
HOST: 
TYPE: BUILT_IN
PASSWORD_POLICY: {'status': {}}

NAME: root
HOST: %
TYPE: BUILT_IN
PASSWORD_POLICY: {'status': {}}

[2026-02-04 17:37:31] ==========================================
[2026-02-04 17:37:31] 명령어: 데이터베이스 목록
[2026-02-04 17:37:31] ==========================================
NAME: mysql
CHARSET: utf8mb3
COLLATION: utf8mb3_general_ci

NAME: information_schema
CHARSET: utf8mb3
COLLATION: utf8mb3_general_ci

NAME: performance_schema
CHARSET: utf8mb4
COLLATION: utf8mb4_0900_ai_ci

NAME: sys
CHARSET: utf8mb4
COLLATION: utf8mb4_0900_ai_ci

NAME: myapp_db
CHARSET: utf8mb4
COLLATION: utf8mb4_unicode_ci
[2026-02-04 17:37:33] 
[2026-02-04 17:37:33] ==========================================
[2026-02-04 17:37:33] MySQL Cloud SQL 설치 작업 완료
[2026-02-04 17:37:33] ==========================================
[2026-02-04 17:37:33] 인스턴스 이름: mysql-dev
[2026-02-04 17:37:33] 데이터베이스: myapp_db
[2026-02-04 17:37:33] IP 주소: 34.22.87.246
[2026-02-04 17:37:33] 연결 이름: gen-lang-client-0725900816:asia-northeast3:mysql-dev
[2026-02-04 17:37:33] root 비밀번호: MySecurePassword123!
[2026-02-04 17:37:33] appuser 비밀번호: AppPassword123!
[2026-02-04 17:37:33] 
[2026-02-04 17:37:33] 연결 명령어:
[2026-02-04 17:37:33]   gcloud sql connect mysql-dev --user=root
[2026-02-04 17:37:33] 
[2026-02-04 17:37:33] 로그 파일 위치: /home/rodem/mysql-setup-log-20260204_173259.txt
[2026-02-04 17:37:33] ==========================================

로그 파일이 생성되었습니다: /home/rodem/mysql-setup-log-20260204_173259.txt
rodem@cloudshell:~ (gen-lang-client-0725900816)$ ^C
rodem@cloudshell:~ (gen-lang-client-0725900816)$ cat  /home/rodem/mysql-setup-log-20260204_173259.txt
[2026-02-04 17:32:59] ==========================================
[2026-02-04 17:32:59] MySQL Cloud SQL 설치 작업 시작
[2026-02-04 17:33:01] 프로젝트: gen-lang-client-0725900816
[2026-02-04 17:33:01] ==========================================

[2026-02-04 17:33:01] ==========================================
[2026-02-04 17:33:01] 명령어: Cloud SQL Admin API 활성화
[2026-02-04 17:33:01] ==========================================
Operation "operations/acat.p2-212772069233-e2d9e460-79a5-491a-a862-3c7520b02364" finished successfully.
[2026-02-04 17:33:08] ✓ 성공: Cloud SQL Admin API 활성화 완료

[2026-02-04 17:33:08] ==========================================
[2026-02-04 17:33:08] 명령어: 활성화된 API 확인
[2026-02-04 17:33:08] ==========================================
NAME: sqladmin.googleapis.com

[2026-02-04 17:33:11] ==========================================
[2026-02-04 17:33:11] 명령어: MySQL 인스턴스 생성 (5-10분 소요)
[2026-02-04 17:33:11] ==========================================
Creating Cloud SQL instance for MYSQL_8_0...
...............done.
Created [https://sqladmin.googleapis.com/sql/v1beta4/projects/gen-lang-client-0725900816/instances/mysql-dev].
NAME: mysql-dev
DATABASE_VERSION: MYSQL_8_0
LOCATION: asia-northeast3-a
TIER: db-n1-standard-1
PRIMARY_ADDRESS: 34.22.87.246
PRIVATE_ADDRESS: -
STATUS: RUNNABLE
[2026-02-04 17:37:14] ✓ 성공: MySQL 인스턴스 생성 완료

[2026-02-04 17:37:14] ==========================================
[2026-02-04 17:37:14] 명령어: root 비밀번호 설정
[2026-02-04 17:37:14] ==========================================
Updating Cloud SQL user...
.done.
[2026-02-04 17:37:17] ✓ 성공: root 비밀번호 설정 완료

[2026-02-04 17:37:17] ==========================================
[2026-02-04 17:37:17] 명령어: 데이터베이스 생성
[2026-02-04 17:37:17] ==========================================
Creating Cloud SQL database...
.done.
Created database [myapp_db].
charset: utf8mb4
collation: utf8mb4_unicode_ci
instance: mysql-dev
name: myapp_db
project: gen-lang-client-0725900816
[2026-02-04 17:37:20] ✓ 성공: 데이터베이스 생성 완료

[2026-02-04 17:37:20] ==========================================
[2026-02-04 17:37:20] 명령어: 애플리케이션 사용자 생성
[2026-02-04 17:37:20] ==========================================
Creating Cloud SQL user...
.done.
Created user [appuser].
[2026-02-04 17:37:24] ✓ 성공: 애플리케이션 사용자 생성 완료

[2026-02-04 17:37:24] ==========================================
[2026-02-04 17:37:24] 명령어: 인스턴스 상세 정보
[2026-02-04 17:37:24] ==========================================
backendType: SECOND_GEN
connectionName: gen-lang-client-0725900816:asia-northeast3:mysql-dev
createTime: '2026-02-04T17:33:14.203Z'
databaseInstalledVersion: MYSQL_8_0_43
databaseVersion: MYSQL_8_0
etag: 5dae6496d0978002282f580e36f341689af0d171997340c2e5c57194beaba931
gceZone: asia-northeast3-a
geminiConfig:
  activeQueryEnabled: false
  entitled: false
  flagRecommenderEnabled: true
  indexAdvisorEnabled: false
includeReplicasForMajorVersionUpgrade: false
instanceType: CLOUD_SQL_INSTANCE
ipAddresses:
- ipAddress: 34.22.87.246
  type: PRIMARY
kind: sql#instance
maintenanceVersion: MYSQL_8_0_43.R20260117.02_02
name: mysql-dev
project: gen-lang-client-0725900816
region: asia-northeast3
satisfiesPzi: true
selfLink: https://sqladmin.googleapis.com/sql/v1beta4/projects/gen-lang-client-0725900816/instances/mysql-dev
serverCaCert:
  cert: |-
    -----BEGIN CERTIFICATE-----
    MIIDfzCCAmegAwIBAgIBADANBgkqhkiG9w0BAQsFADB3MS0wKwYDVQQuEyRkNzI0
    Y2JmMi02NzczLTRlMzctYjY3Yy1jYzBiYTU0Nzg0ZWMxIzAhBgNVBAMTGkdvb2ds
    ZSBDbG91ZCBTUUwgU2VydmVyIENBMRQwEgYDVQQKEwtHb29nbGUsIEluYzELMAkG
    A1UEBhMCVVMwHhcNMjYwMjA0MTczMzU5WhcNMzYwMjAyMTczNDU5WjB3MS0wKwYD
    VQQuEyRkNzI0Y2JmMi02NzczLTRlMzctYjY3Yy1jYzBiYTU0Nzg0ZWMxIzAhBgNV
    BAMTGkdvb2dsZSBDbG91ZCBTUUwgU2VydmVyIENBMRQwEgYDVQQKEwtHb29nbGUs
    IEluYzELMAkGA1UEBhMCVVMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIB
    AQCyqq3vc26PsPJfTI4/d5JgV6wTLT+GsCg2M2w37dhs1fwLHRmCOznlP4bV3RBO
    9rQEiV7EQ3SjGqm9sXcNjFcCZlrwot2bpTN7UHIyx+dBJ6MFZ6FEjnZyN3YaK6SV
    uGqPlh5+9b+pad/vaOp2MOoLxJdeYBcvaaz6+4jNajakC3B8+Ol+M4gEU4nWHxr6
    2CpXTI3++We0kyPKwO0oWWynIt6rhaALGSD/DfcqCLtb6YTTBhp6vcxEDNFAm483
    MyhsJHqIW88m1apV84heMwOEHEDOIgglHxb4smgKvlzUSEWAQWKnbdTU8FoZ2RmU
    DNlKUUtc8HuDdsdQv9mkR9GJAgMBAAGjFjAUMBIGA1UdEwEB/wQIMAYBAf8CAQAw
    DQYJKoZIhvcNAQELBQADggEBAGQLrc0Ik84lWTZo++ZWBIEq41FrSKWSWNvfzQl2
    11vQRC3t5nmHBZm1wD7Q8uqItES6UuTObZz6XJvSnYZhx+Yh1sbbhCZPSobiGiuw
    eDN7995N/luC/7Z2j+7PfrNMCvP2xaJjp9oDuuwYSOs/ziHzamnqzDrOQ2M0UH+j
    +d4ffIQYMcXWFH4RNPZMgD80mqlfALLyDTI7p4lBX0nfU57jsXvn+Pui7QhrG6D8
    bodt5JSxGsmmPhyP6C25/9yjdH9QDq3aaW0J7KsiNC1AuEv1A4ChZ2g1sIR7Y5XV
    x0X4OAYpefrEjTXCzIMOJdHQyGua29+UsbEaPtm7Z9PA/vo=
    -----END CERTIFICATE-----
  certSerialNumber: '0'
  commonName: C=US,O=Google\, Inc,CN=Google Cloud SQL Server CA,dnQualifier=d724cbf2-6773-4e37-b67c-cc0ba54784ec
  createTime: '2026-02-04T17:33:59.402Z'
  expirationTime: '2036-02-02T17:34:59.402Z'
  instance: mysql-dev
  kind: sql#sslCert
  sha1Fingerprint: ca42c5fe2d5c7fa97d40d980467beeb2404d4586
serviceAccountEmailAddress: p212772069233-fy7qws@gcp-sa-cloud-sql.iam.gserviceaccount.com
settings:
  activationPolicy: ALWAYS
  availabilityType: ZONAL
  backupConfiguration:
    backupRetentionSettings:
      retainedBackups: 7
      retentionUnit: COUNT
    backupTier: STANDARD
    enabled: true
    kind: sql#backupConfiguration
    startTime: 03:00
    transactionLogRetentionDays: 7
    transactionalLogStorageState: TRANSACTIONAL_LOG_STORAGE_STATE_UNSPECIFIED
  connectorEnforcement: NOT_REQUIRED
  dataDiskSizeGb: '20'
  dataDiskType: PD_SSD
  deletionProtectionEnabled: false
  edition: ENTERPRISE
  ipConfiguration:
    ipv4Enabled: true
    message: Configuring authorized network or using CloudSQL auth proxy or language
      connectors is a prerequisite for connecting to Public IP. Please refer to the
      documentation for more details https://cloud.google.com/sql/docs/mysql/authorize-networks.
    requireSsl: false
    serverCaMode: GOOGLE_MANAGED_INTERNAL_CA
    serverCertificateRotationMode: SERVER_CERTIFICATE_ROTATION_MODE_UNSPECIFIED
    sslMode: ALLOW_UNENCRYPTED_AND_ENCRYPTED
  kind: sql#settings
  locationPreference:
    kind: sql#locationPreference
    zone: asia-northeast3-a
  pricingPlan: PER_USE
  replicationLagMaxSeconds: 31536000
  replicationType: SYNCHRONOUS
  settingsVersion: '1'
  storageAutoResize: true
  storageAutoResizeLimit: '0'
  tier: db-n1-standard-1
sqlNetworkArchitecture: NEW_NETWORK_ARCHITECTURE
state: RUNNABLE
upgradableDatabaseVersions:
- displayName: MySQL 8.0.18
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_18
- displayName: MySQL 8.0.26
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_26
- displayName: MySQL 8.0.27
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_27
- displayName: MySQL 8.0.28
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_28
- displayName: MySQL 8.0.29
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_29
- displayName: MySQL 8.0.30
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_30
- displayName: MySQL 8.0.31
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_31
- displayName: MySQL 8.0.32
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_32
- displayName: MySQL 8.0.33
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_33
- displayName: MySQL 8.0.34
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_34
- displayName: MySQL 8.0.35
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_35
- displayName: MySQL 8.0.36
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_36
- displayName: MySQL 8.0.37
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_37
- displayName: MySQL 8.0.39
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_39
- displayName: MySQL 8.0.40
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_40
- displayName: MySQL 8.0.41
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_41
- displayName: MySQL 8.0.42
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_42
- displayName: MySQL 8.0.44
  majorVersion: MYSQL_8_0
  name: MYSQL_8_0_44
- displayName: MySQL 8.4
  majorVersion: MYSQL_8_4
  name: MYSQL_8_4

[2026-02-04 17:37:26] ==========================================
[2026-02-04 17:37:26] 명령어: 인스턴스 IP 주소
[2026-02-04 17:37:26] ==========================================
IP 주소: 34.22.87.246

[2026-02-04 17:37:27] ==========================================
[2026-02-04 17:37:27] 명령어: 인스턴스 연결 이름
[2026-02-04 17:37:27] ==========================================
연결 이름: gen-lang-client-0725900816:asia-northeast3:mysql-dev

[2026-02-04 17:37:29] ==========================================
[2026-02-04 17:37:29] 명령어: MySQL 사용자 목록
[2026-02-04 17:37:29] ==========================================
NAME: appuser
HOST: 
TYPE: BUILT_IN
PASSWORD_POLICY: {'status': {}}

NAME: root
HOST: %
TYPE: BUILT_IN
PASSWORD_POLICY: {'status': {}}

[2026-02-04 17:37:31] ==========================================
[2026-02-04 17:37:31] 명령어: 데이터베이스 목록
[2026-02-04 17:37:31] ==========================================
NAME: mysql
CHARSET: utf8mb3
COLLATION: utf8mb3_general_ci

NAME: information_schema
CHARSET: utf8mb3
COLLATION: utf8mb3_general_ci

NAME: performance_schema
CHARSET: utf8mb4
COLLATION: utf8mb4_0900_ai_ci

NAME: sys
CHARSET: utf8mb4
COLLATION: utf8mb4_0900_ai_ci

NAME: myapp_db
CHARSET: utf8mb4
COLLATION: utf8mb4_unicode_ci
[2026-02-04 17:37:33] 
[2026-02-04 17:37:33] ==========================================
[2026-02-04 17:37:33] MySQL Cloud SQL 설치 작업 완료
[2026-02-04 17:37:33] ==========================================
[2026-02-04 17:37:33] 인스턴스 이름: mysql-dev
[2026-02-04 17:37:33] 데이터베이스: myapp_db
[2026-02-04 17:37:33] IP 주소: 34.22.87.246
[2026-02-04 17:37:33] 연결 이름: gen-lang-client-0725900816:asia-northeast3:mysql-dev
[2026-02-04 17:37:33] root 비밀번호: MySecurePassword123!
[2026-02-04 17:37:33] appuser 비밀번호: AppPassword123!
[2026-02-04 17:37:33] 
[2026-02-04 17:37:33] 연결 명령어:
[2026-02-04 17:37:33]   gcloud sql connect mysql-dev --user=root
[2026-02-04 17:37:33] 
[2026-02-04 17:37:33] 로그 파일 위치: /home/rodem/mysql-setup-log-20260204_173259.txt
[2026-02-04 17:37:33] ==========================================
