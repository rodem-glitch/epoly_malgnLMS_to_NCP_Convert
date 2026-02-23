제목: [NCP 인프라] 서버 외부 접속 및 네트워크 라우팅 설정 요청

굿어스데이터 송재혁 과장님께,

안녕하세요.
NCP 환경에 LMS 시스템 이관 작업을 진행하고 있으며, 현재 네트워크 구성 관련하여 몇 가지 설정 지원을 요청드립니다.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

■ 현재 인프라 현황

  서버명            비공인 IP        Subnet          상태
  ─────────────────────────────────────────────────
  newkl-web01       192.168.1.6     web-subnet       운영중 (Private)
  newkl-was01       192.168.2.6     was-subnet       부팅중 (Private)
  NAT Gateway       27.96.146.74    nat-subnet       운영중
  Load Balancer     27.96.144.249   lb-public-subnet 운영중

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

■ 문제점 (3건)

  1. 서버 아웃바운드 인터넷 불가
     - web-subnet, was-subnet의 Route Table에 NAT Gateway 경로가 미설정
     - 서버에서 외부(github.com 등) 접속이 되지 않아 패키지 설치 및 소스 배포 불가

  2. 서버 SSH 인바운드 접속 불가
     - 두 서버 모두 Private Subnet에 위치하여 공인 IP 할당 불가
     - 외부에서 SSH(22) 접속이 불가하여 CI/CD 배포 및 원격 관리 불가
     - SSL VPN(newkl)이 2/19 생성 이후 '생성중' 상태에서 변경되지 않음

  3. Public Subnet 서버 생성 불가
     - web-public-subnet(Public)에 신규 서버 생성 시 서버 스펙 선택이 되지 않음
     - 유사 서버 생성, Network Interface 할당 등 대안도 Private→Public 전환 불가

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

■ 원인 분석

  1. Route Table 미설정
     - was-subnet, web-subnet의 Route Table에 0.0.0.0/0 → NAT Gateway 라우팅이
       없어 Private Subnet 서버들이 아웃바운드 통신 불가

  2. SSL VPN 장애
     - SSL VPN 인스턴스(newkl, ID: 108494553)가 2026-02-19 생성 이후
       3일간 '생성중' 상태로 고착되어 삭제/설정 변경 모두 불가

  3. Public Subnet 제약
     - web-public-subnet에서 서버 생성 시 서버 스펙 목록이 표시되지 않아
       Public Subnet에 서버를 배치할 수 없음

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

■ 요청 사항

  1. Route Table 설정 (긴급)
     - was-subnet, web-subnet의 Route Table에 아래 경로를 추가해 주십시오.
       목적지: 0.0.0.0/0 → Target: NATGW (newkl-nat)
     - 이 설정이 되어야 서버에서 Docker 이미지 pull, GitHub 소스 clone 등
       배포 작업이 가능합니다.

  2. SSL VPN 복구 또는 SSH 접속 방안 마련
     - SSL VPN(newkl) 인스턴스 상태 확인 및 복구를 부탁드립니다.
     - 또는 서버에 SSH 접속이 가능한 대안(Port Forwarding, Bastion 서버 등)을
       안내해 주시면 감사하겠습니다.

  3. Public Subnet 서버 생성 이슈 확인
     - web-public-subnet에서 서버 스펙 선택이 불가한 원인 확인을 요청드립니다.

  위 3건 중 1번(Route Table)이 해결되면 서버 콘솔에서 직접 배포를 진행할 수
  있으므로, 우선적으로 처리를 부탁드리겠습니다.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

■ 참고: 현재 구성된 서비스 아키텍처

  사용자 → Load Balancer (27.96.144.249:80)
            → newkl-web01 (Nginx, 192.168.1.6)
              → newkl-was01 (Docker, 192.168.2.6)
                ├ lms-api    (Spring Boot :8081)
                ├ lms-resin  (JSP/Resin   :8080)
                └ lms-qdrant (Vector DB   :6333)
              → Cloud DB (growai-db.vpc-cdb.ntruss.com:3306)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

확인 부탁드리며, 궁금하신 사항이 있으시면 말씀해 주십시오.
감사합니다.
