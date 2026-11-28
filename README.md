# 설매 · 미디어아트와 눈썰매장

React 방문자 사이트와 Java 21 Spring Boot 예약 서버입니다. 이메일 또는 소셜 로그인 후 잔여석이 있는 회차를 선택하면 서버가 좌석을 확보하고, 토스페이먼츠 승인 완료 시 예약을 확정합니다.

**[개발 ERD 열기](docs/ERD.md)** · **[테이블 생성 SQL](backend/src/main/resources/schema.sql)** · **[Figma 디자인](https://www.figma.com/design/tXGsN96m8NMhqJzDSGVx4t/?node-id=3-32)**

Figma에서 [로그인](https://www.figma.com/design/tXGsN96m8NMhqJzDSGVx4t/?node-id=20-105), [예매](https://www.figma.com/design/tXGsN96m8NMhqJzDSGVx4t/?node-id=4-907), [결제 결과](https://www.figma.com/design/tXGsN96m8NMhqJzDSGVx4t/?node-id=4-955), [내 예약](https://www.figma.com/design/tXGsN96m8NMhqJzDSGVx4t/?node-id=20-227)을 확인할 수 있습니다. Figma의 회차·금액·결제 결과는 예시 데이터입니다.

## 로컬 실행

Java **JDK 21**, Maven 3.9 이상, Node.js 22.12 이상을 준비합니다. 저장소 루트에서 터미널 두 개를 엽니다.

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

```bash
cd frontend
npm ci
npm run dev
```

브라우저에서 http://localhost:5173 을 엽니다. 프론트와 백엔드 요청은 Vite 프록시를 통해 같은 출처로 처리합니다. 안내와 회차 목록은 외부 키 없이 확인할 수 있습니다. 소셜 로그인과 결제는 아래 환경변수를 백엔드 프로세스에 설정한 후 서버를 재시작하면 활성화됩니다. 프론트 개발 서버 주소와 인증 제공자에 등록한 주소에서 localhost 표기를 통일하세요.

## 기본 로그인과 OAuth2 로그인

**기본 로그인**은 외부 키 없이 사용할 수 있습니다. 로그인 화면에서 **이메일 회원가입**을 선택하고 이메일과 비밀번호(10~128자)를 입력합니다. 가입 후 같은 이메일과 비밀번호로 로그인하면 예매를 진행할 수 있습니다. 비밀번호는 PBKDF2 해시로 저장합니다.

**소셜 로그인**은 아래에서 사용할 제공자의 앱을 등록하고 해당 키 쌍을 서버 환경변수로 설정한 뒤 재시작합니다. 웹 앱의 승인된 리디렉션 주소는 아래 값과 정확히 일치해야 합니다. 배포 시 localhost 대신 배포 사이트의 HTTPS 주소를 사용합니다.

| 제공자 | 백엔드 환경변수 | 로컬 콜백 |
| --- | --- | --- |
| Google (OpenID Connect) | GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET | `http://localhost:5173/login/oauth2/code/google` |
| 카카오 | KAKAO_CLIENT_ID (REST API 키), KAKAO_CLIENT_SECRET | `http://localhost:5173/login/oauth2/code/kakao` |
| 네이버 | NAVER_CLIENT_ID, NAVER_CLIENT_SECRET | `http://localhost:5173/login/oauth2/code/naver` |

각 제공자의 앱 콘솔에서 웹 서비스 로그인을 활성화하세요. 키가 설정된 제공자의 버튼만 활성화됩니다. 회원은 제공자와 고유 ID 조합으로 구분하며 다른 로그인 방식에서 같은 이메일을 사용해도 예약 이력이 자동 통합되지 않습니다.

`POST /api/auth/register`는 JSON의 email, password를 받으며 성공 시 201을 반환합니다. `POST /api/auth/login`은 폼 인코딩된 email, password를 받으며 성공 시 204와 로그인 세션을 반환합니다. 두 요청 모두 먼저 `GET /api/session`에서 받은 CSRF 토큰과 동일한 세션 쿠키가 필요합니다.


## 토스페이먼츠 결제

1. 토스페이먼츠 개발자센터에서 결제창용 **API 개별 연동 키**의 클라이언트 키와 시크릿 키를 같은 상점·모드의 쌍으로 준비합니다.
2. 백엔드에 키를 설정합니다. 로컬에서는 테스트 키와 demo 프로필로 회차와 테스트 승인을 확인합니다.
3. 이용권·회차·인원을 선택하고 **예약하고 결제하기**를 누릅니다. 카드 결제창을 거쳐 돌아오면 서버가 주문 소유자, 주문 ID와 금액을 확인하고 결제사 승인 API를 호출합니다. 성공 시 **내 예약**에 확정 상태가 표시됩니다.
4. 결제창을 닫았으면 내 예약에서 유효기간 내 재결제할 수 있습니다. 승인 확인 중에는 **승인 다시 확인**을 선택합니다.

| 백엔드 환경변수 | 설정값 |
| --- | --- |
| APP_ORIGIN | 로컬 기본값 http://localhost:5173. 운영은 방문자가 접속하는 HTTPS origin. 끝에 슬래시를 붙이지 않음 |
| KAKAO_CLIENT_ID / KAKAO_CLIENT_SECRET | 카카오 REST API 키 / 로그인 Client Secret |
| TOSS_CLIENT_KEY / TOSS_SECRET_KEY | 토스페이먼츠 API 개별 연동 키 쌍 |
| COOKIE_SECURE | HTTPS 운영 환경에서 true |
| DB_URL / DB_USER / DB_PASSWORD | 기본값은 파일형 H2, sa, 빈 비밀번호. 운영 시 데이터 볼륨과 접근 자격 설정 |

실거래에는 상점 계약과 라이브 키가 필요합니다. 시크릿은 서버 환경변수 또는 배포 서비스의 비밀 저장소에만 설정하세요. 운영 웹 서버는 `/api/`, `/oauth2/`, `/login/oauth2/`를 Spring으로 전달하고, `/payment/success`와 `/payment/fail`을 포함한 프론트 경로는 React의 index.html로 전달합니다.

[토스페이먼츠 결제창 가이드](https://docs.tosspayments.com/guides/v2/payment-window/integration)

## 회차 및 저장

일반 프로필은 운영 회차가 없는 상태로 시작합니다. 실제 회차는 운영 DB의 VISIT_SLOT에 등록합니다. demo 프로필은 다음 7일에 14시 회차, 종류별 30석, 시연용 1인 가격을 생성합니다. 운영 DB와 demo DB를 분리하고 운영에서는 demo 프로필을 사용하지 마세요. 통합권은 별도의 정원을 가진 상품입니다. 판매 시 단품·통합권 정원을 실제 시설 수용 인원에 맞게 배정하세요.

기본 DB는 백엔드 작업 디렉터리의 `data/`에 저장되며 서버 재시작 후에도 예약 내역이 유지됩니다. 운영 날짜, 가격, 주소는 서비스 운영자가 정합니다.

## API

| 메서드 | 경로 | 역할 |
| --- | --- | --- |
| GET | /api/session | 로그인 상태, CSRF 토큰, 공개 결제 설정 |
| GET | /api/experiences, /api/slots | 체험, 판매 회차·잔여석 조회 |
| POST | /api/reservations | 로그인 회원의 좌석 확보. slotId, requestId(UUID), adults, children |
| GET | /api/reservations, /api/reservations/{id} | 내 예약 목록·상세 |
| DELETE | /api/reservations/{id} | 결제 대기 예약 취소 |
| POST | /api/payments/confirm | 결제 인증 후 서버 승인. orderId, paymentKey, amount |
| POST | /api/payments/{id}/retry | 결과가 불명확한 승인 재확인 |
| POST | /api/logout | 세션 종료 |

쓰기 요청은 세션 쿠키와 `X-CSRF-TOKEN` 헤더를 사용합니다. 결제사 승인 전에는 예약이 확정되지 않습니다. 결제 후 환불은 현재 상점 관리자 절차로 처리하며, 관리자 환불 후 예약 상태와 좌석을 대조해야 합니다.

## 결제 장애 시 확인 절차

| 상황 | 예약 및 좌석 처리 |
| --- | --- |
| 결제창 이탈 또는 카드 인증 실패 | 내 예약에서 결제 대기 상태와 기한을 확인합니다. 보유 시간이 끝나면 좌석이 풀립니다. |
| 승인 응답 지연, 연결 종료, 429 또는 5xx | 주문 ID로 결제사 상태를 조회합니다. 결과가 불분명하면 PROCESSING 상태와 좌석을 유지합니다. |
| 브라우저 종료 또는 승인 후 DB 기록 실패 | 서버가 PROCESSING 주문을 매분 최대 20건 조회합니다. 같은 주문의 자동 조회는 최소 5분 간격이며 별도의 승인 요청을 보내지 않습니다. |
| 주문·금액·결제 키 불일치 또는 필수 값 누락 | 확정을 보류합니다. 주문 ID로 원거래를 대조합니다. |
| 장기 미해결 또는 외부 환불 | 서버 로그의 `Payment reconciliation pending for order` 주문 번호로 상점의 거래 상태, 승인 금액, 예약 상태를 대조합니다. 부분 취소·관리자 환불은 별도 대사가 필요합니다. |

테스트 상점 배포 후 카드 인증 성공·실패·사용자 이탈과 리디렉션 후 로그인 세션 유지 여부를 확인하세요. 결제사 장애 중에는 좌석 보유가 길어질 수 있습니다.

## 빌드 및 검증

- 프론트엔드: `cd frontend` 후 `npm ci && npm run build`
- 백엔드: `cd backend` 후 `mvn clean package`
- 통합 테스트는 임시 DB와 결제사 모의 응답을 사용합니다. 실제 소셜 로그인·토스 테스트 상점 E2E 검증에는 해당 앱 키가 필요합니다.

## 회원가입과 운영 보안 설정

상단 회원가입 또는 `/#signup`에서 이메일, 비밀번호, 비밀번호 확인을 입력합니다. 이미 등록된 이메일에도 같은 응답을 반환하며 기존 비밀번호는 유지합니다. 가입만으로 이메일 주소 소유 여부가 인증되지는 않습니다.

운영 서버는 HTTPS에서 `SPRING_PROFILES_ACTIVE=prod`와 HTTPS `APP_ORIGIN`을 설정합니다. prod 프로필은 세션 쿠키에 Secure를 적용합니다. 기본 로그인과 가입 요청 제한은 서버 인스턴스 단위입니다. 여러 인스턴스에서는 공통 게이트웨이의 요청 제한과 신뢰할 프록시 설정이 필요합니다.

### 운영 서버 시작 조건

현재 배포 구성은 서버 한 대와 영구 볼륨의 H2 파일 DB를 기준으로 합니다. prod 프로필은 시작 전에 다음 값을 검사합니다.

| 설정 | 운영 값 |
| --- | --- |
| SPRING_PROFILES_ACTIVE | prod. demo와 동시 실행 불가 |
| APP_ORIGIN | 경로와 끝 슬래시 없는 HTTPS 서비스 주소 |
| DB_URL / DB_PASSWORD | 예: jdbc:h2:file:/var/lib/sseolmae/bookings 및 DB 비밀번호. 기존 DB 사용 시 실제 자격 증명과 일치해야 함 |
| TOSS_CLIENT_KEY / TOSS_SECRET_KEY | 판매 시 API 개별 연동 라이브 키 한 쌍. 판매 전에는 둘 다 비워 조회만 허용 |
| OAuth 키 | 활성화할 제공자마다 ID와 Secret을 함께 등록 |

DB 디렉터리를 영구 볼륨에 연결하고 백업 복구를 검증해야 합니다. 설정 검사는 볼륨의 내구성이나 외부 키의 유효성을 보장하지 않습니다. 토스 테스트 키를 사용하는 검증 서버는 별도 DB와 HTTPS 주소를 사용하고 실서비스와 분리합니다.
