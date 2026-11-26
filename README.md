# Sseolmae

실내 미디어아트 전시와 야외 눈썰매장의 소개, 이용 안내 및 테스트 예매를 제공하는 웹 프로젝트입니다.

[Figma 시안](https://www.figma.com/design/tXGsN96m8NMhqJzDSGVx4t/?node-id=3-32)

## 실행

Java 21, Maven 3.9 이상과 Node.js 22.12 이상을 사용합니다. 터미널 두 개에서 각각 실행합니다.

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm ci
npm run dev
```

프론트엔드는 http://localhost:5173, 백엔드는 http://localhost:8080에서 실행합니다. 개발 서버와 Vite 미리보기는 `/api` 요청을 백엔드로 전달합니다. 배포 시에는 같은 경로를 Spring Boot에 전달하도록 웹 서버를 설정합니다.

## 기본 설계

| 구성 | 역할 |
| --- | --- |
| `frontend/` | React와 Vite. 체험 소개, 운영 안내, 테스트 예매 입력과 접수 결과 |
| `backend/` | Spring Boot REST API. 체험 목록과 예매 접수·조회, 요청 검증 |
| `ReservationService` | 서울 시간대 기준 날짜·인원 검증 및 UUID로 테스트 접수 내역 저장 |

사용자는 체험과 방문 정보를 확인한 후 이용권, 날짜, 인원을 선택합니다. 서버는 입력을 검증하고 접수 번호를 반환합니다. 화면은 이 번호와 방문 정보를 표시합니다. 1~20명은 테스트 접수용 인원 조건입니다.

| 메서드 | 경로 | 응답 |
| --- | --- | --- |
| GET | `/api/experiences` | 체험 목록 |
| POST | `/api/reservations` | 접수 생성 `201`, 잘못된 입력 `400` |
| GET | `/api/reservations/{id}` | 접수 조회 `200`, 없는 번호 `404` |

접수 요청은 `visitDate`(YYYY-MM-DD), `ticketType`(`MEDIA_ART`, `SNOW_SLED`, `COMBO`), `adults`, `children`으로 구성됩니다. 상태 `DEMO_RECEIVED`는 테스트 접수를 뜻합니다.

테스트 예매는 결제와 재고 확인 없이 서버 메모리에 저장되며 서버를 재시작하면 초기화됩니다. 요금, 운영시간, 주소는 확정 전까지 ‘추후 안내’로 표시합니다.

## 빌드

- 프론트엔드: `cd frontend` 후 `npm ci && npm run build`
- 백엔드: `cd backend` 후 `mvn package`
