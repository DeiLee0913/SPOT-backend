# SPOT (Study + Pot) — Backend

지인 기반 소규모 스터디 그룹이 일일 학습 목표를 설정하고 학습 시간을 측정·공유하는 서비스의 백엔드입니다.
그룹원들의 당일 학습 현황을 한 화면에서 공유해 혼자 공부할 때의 동기 부족을 해결하는 것이 목표입니다.

클라이언트는 모바일 웹 기준이며 프론트엔드는 별도 리포(`spot-frontend`)에서 관리합니다.

## 기술 스택

- Java 21 / Spring Boot 3.5 (Web, Data JPA, Security, Validation)
- MySQL 8 + Flyway
- JWT (jjwt) 기반 인증, 네이버 소셜 로그인
- Gradle
- Docker + Render(앱) / Aiven(MySQL) 배포

## 주요 기능

- **인증**: 네이버 OAuth 로그인 후 JWT 발급
- **그룹**: 1인 1그룹, 초대 코드 + 생성자 승인 가입, 최대 20명
- **목표**: 일일 학습 목표 설정, 개인 기본(DEFAULT) 목표, **오전 11시(KST)** 설정 마감
- **타이머 세션**: 카테고리별 시작/종료, 하루 다중 세션 누적, "이어서 공부하기" 복구
- **수동 세션**: 타이머 누락 시 시작·종료 시각 직접 입력 (시간 겹침 검증)
- **세션 정책**: 등록된 세션은 수정 불가, 삭제만 가능 (타이머/수동 공통)
- **그룹 대시보드**: 그룹원별 오늘 누적 시간·달성률, 주간 점수/순위, 최근 7일 히스토리
- **스케줄러**: 11:00 기본 목표 자동 적용, 06:00 경과 세션 정리

## 핵심 도메인 규칙

- **Study Day**: 매일 06:00(KST) 기준으로 날짜가 전환됩니다. 자정~06:00 학습은 전날 기록으로 포함됩니다.
- **목표 마감**: 당일 **11:00** 이후에는 오늘 목표를 변경할 수 없고, 미설정 시 개인 DEFAULT 목표가 적용됩니다.
- **가입 첫날 예외**: 가입 study day에 아직 `USER_SET` 오늘 목표가 없으면 마감 이후에도 `PUT /goals/today`로 **최초 1회** 설정할 수 있습니다. `GET /me`의 `needsTodayGoalSetup`으로 온보딩 분기에 사용합니다.
- **세션 소스**: `TIMER` / `MANUAL`로 구분하며, 시간이 겹치는 세션은 등록을 거절합니다.

## API 개요

모든 응답은 공통 래퍼를 사용합니다.

```json
{ "success": true, "data": { }, "error": null }
```

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/health` | X | 헬스체크 |
| GET | `/me` | O | 내 정보 + 그룹 소속 + 진행 중 세션 |
| PUT | `/me/default-goal` | O | 기본 목표 시간 변경 |
| GET/PUT | `/goals/today` | O | 오늘 목표 조회/설정 |
| POST | `/sessions/start` | O | 타이머 세션 시작 |
| POST | `/sessions/{id}/end` | O | 타이머 세션 종료 |
| POST | `/sessions/manual` | O | 수동 세션 등록 |
| DELETE | `/sessions/{id}` | O | 세션 삭제 |
| GET | `/sessions/today`, `/sessions/open` | O | 오늘 세션 목록 / 진행 중 세션 |
| POST | `/groups`, `/groups/join` | O | 그룹 생성 / 초대 코드 가입 |
| GET | `/groups/me` | O | 내 그룹 상세 |
| POST | `/groups/me/leave` | O | 그룹 탈퇴(생성자는 승인권 이전) |
| GET/POST | `/groups/me/join-requests/...` | O | 가입 요청 목록 / 승인 / 거절 |
| GET | `/groups/me/dashboard` | O | 그룹 대시보드 |

인증이 필요한 API는 `Authorization: Bearer <JWT>` 헤더가 필요합니다.

## 로컬 실행

사전 준비: JDK 21, MySQL 8

### 1. 데이터베이스

```sql
CREATE DATABASE IF NOT EXISTS spot
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'spot'@'localhost' IDENTIFIED BY 'spot';
GRANT ALL PRIVILEGES ON spot.* TO 'spot'@'localhost';
FLUSH PRIVILEGES;
```

테이블은 앱 실행 시 Flyway가 자동으로 생성합니다.

### 2. 환경 변수

| 변수 | 설명 |
|------|------|
| `DB_URL` | JDBC URL (예: `jdbc:mysql://localhost:3306/spot?characterEncoding=UTF-8`) |
| `DB_USERNAME` / `DB_PASSWORD` | DB 접속 계정 |
| `JWT_SECRET` | JWT 서명 비밀키 (32바이트 이상 권장) |
| `JWT_EXPIRATION_MINUTES` | 토큰 만료(분) |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` / `NAVER_REDIRECT_URI` | 네이버 로그인 설정 |
| `DEFAULT_GOAL_MINUTES` | 신규 사용자 기본 목표 시간 |

### 3. 실행

```bash
./gradlew bootRun
```

기동 후 `http://localhost:8080/health` 로 확인합니다.
`dev` 프로파일(`SPRING_PROFILES_ACTIVE=dev`)로 실행하면 `POST /auth/dev/token` 으로 소셜 로그인 없이 테스트용 JWT를 발급받을 수 있습니다.

## 배포

- `Dockerfile` — 앱 빌드/실행 이미지
- `render.yaml` — Render 서비스 정의

## 프로젝트 구조

```
src/main/java/com/spot
├── api/        # REST 컨트롤러 + DTO
├── auth/       # 인증(JWT, 필터, 현재 사용자)
├── domain/     # user / group / goal / session / dashboard
├── scheduler/  # 기본 목표·세션 정리 스케줄러
├── config/     # Security, Web, Scheduling, Clock
└── common/     # 공통 응답·예외 처리
src/main/resources/db/migration  # Flyway 마이그레이션
```
