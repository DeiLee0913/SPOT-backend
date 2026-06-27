# SPOT (Study + Pot) — Backend

지인 기반 소규모 스터디 그룹이 **일일 학습 목표**를 설정하고 **학습 시간**을 측정·공유하는 서비스의 백엔드입니다.
혼자 공부할 때의 동기 부족을 해결하고, 그룹원들의 당일 학습 현황을 한 화면에서 투명하게 공유하는 것을 목표로 합니다.

> 클라이언트는 **모바일 웹**(스마트폰 브라우저 / 토스 미니앱 WebView) 기준입니다. (별도 리포: `spot-frontend`)

---

## 주요 기능

- **인증**: JWT 기반 인증 (소셜 로그인 연동)
- **그룹**: 1인 1그룹, 초대 코드 + 생성자 승인 가입, 최대 20명
- **목표**: 일일 학습 목표 시간 설정, 개인 기본(DEFAULT) 목표, 오전 10시(KST) 설정 마감
- **타이머 세션**: 카테고리별 학습 세션 시작/종료, 하루 다중 세션 누적, 앱 종료 시 "이어서 공부하기" 복구
- **수동 세션**: 타이머 누락 시 시작·종료 시각을 직접 입력해 등록(수정·삭제 지원, 시간 겹침 검증)
- **그룹 대시보드**: 그룹원별 오늘 누적 시간·목표 달성률, 주간 점수/순위, 최근 7일 히스토리
- **스케줄러**: 10:00 기본 목표 자동 적용, 06:00 경과 세션 정리 등

---

## 기술 스택

| 구분 | 내용 |
|------|------|
| 언어/런타임 | Java 21 |
| 프레임워크 | Spring Boot 3.5 (Web, Data JPA, Security, Validation) |
| DB | MySQL 8 |
| 마이그레이션 | Flyway |
| 인증 | JWT (jjwt) |
| 빌드 | Gradle |
| 배포 | Docker + Render (앱) / Aiven (MySQL) |

---

## 도메인 개념

- **Study Day**: 매일 **06:00 (KST)** 기준으로 날짜가 전환됩니다. 자정~06:00 학습은 전날로 귀속됩니다.
- **목표 마감**: 당일 10:00 이후에는 오늘 목표를 변경할 수 없으며, 미설정 시 개인 DEFAULT 목표가 자동 적용됩니다.
- **세션 소스**: `TIMER`(타이머) / `MANUAL`(직접 등록)로 구분합니다. 시간이 겹치는 세션은 등록을 거절합니다.

---

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
| POST/PUT/DELETE | `/sessions`, `/sessions/{id}` | O | 수동 세션 등록/수정/삭제 |
| GET | `/sessions/today`, `/sessions/open` | O | 오늘 세션 목록 / 진행 중 세션 |
| POST | `/groups`, `/groups/join` | O | 그룹 생성 / 초대 코드 가입 |
| GET | `/groups/me` | O | 내 그룹 상세 |
| POST | `/groups/me/leave` | O | 그룹 탈퇴(생성자는 승인권 이전) |
| GET/POST | `/groups/me/join-requests/...` | O | 가입 요청 목록 / 승인 / 거절 |
| GET | `/groups/me/dashboard` | O | 그룹 대시보드 |

> 인증이 필요한 API는 `Authorization: Bearer <JWT>` 헤더가 필요합니다.

---

## 로컬 개발 환경

### 사전 준비

- JDK 21
- MySQL 8 (로컬 설치 또는 Docker)

### 1. 데이터베이스 준비

로컬 MySQL에 데이터베이스와 계정을 생성합니다. (테이블은 앱 실행 시 Flyway가 자동 생성)

```sql
CREATE DATABASE IF NOT EXISTS spot
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'spot'@'localhost' IDENTIFIED BY 'spot';
GRANT ALL PRIVILEGES ON spot.* TO 'spot'@'localhost';
FLUSH PRIVILEGES;
```

### 2. 환경 변수 설정

`.env.example`을 복사해 `.env`를 만들고 값을 채웁니다. (`.env`는 git에 커밋되지 않습니다)

```bash
cp .env.example .env
```

| 변수 | 설명 |
|------|------|
| `DB_URL` | JDBC URL (예: `jdbc:mysql://localhost:3306/spot?characterEncoding=UTF-8`) |
| `DB_USERNAME` / `DB_PASSWORD` | DB 접속 계정 |
| `JWT_SECRET` | JWT 서명 비밀키 (32바이트 이상 권장) |
| `JWT_EXPIRATION_MINUTES` | 토큰 만료(분) |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` / `NAVER_REDIRECT_URI` | 소셜 로그인 설정 |
| `DEFAULT_GOAL_MINUTES` | 신규 사용자 기본 목표 시간 |

### 3. 실행

```bash
./gradlew bootRun
```

서버 기동 후 `http://localhost:8080/health` 로 확인합니다.

> **개발용 토큰**: `dev` 프로파일로 실행하면 `POST /auth/dev/token` 으로 소셜 로그인 없이 테스트용 JWT를 발급받을 수 있습니다. (`SPRING_PROFILES_ACTIVE=dev`)

---

## 배포

Docker 이미지로 빌드되어 **Render**(앱) + **Aiven MySQL**(DB) 무료 환경에 배포하도록 구성되어 있습니다.

- `Dockerfile` — 앱 빌드/실행 이미지
- `render.yaml` — Render 서비스 정의 (시크릿은 Render 대시보드 환경변수로 주입)

배포 시에도 DB 시크릿·JWT·소셜 로그인 값은 코드가 아닌 **환경변수**로 주입합니다.

---

## 프로젝트 구조

```
src/main/java/com/spot
├── api/             # REST 컨트롤러 + DTO
├── auth/            # 인증(JWT, 필터, 현재 사용자)
├── domain/          # user / group / goal / session / dashboard
├── scheduler/       # 기본 목표·세션 정리 스케줄러
├── config/          # Security, Web, Scheduling, Clock
└── common/          # 공통 응답·예외 처리
src/main/resources/db/migration  # Flyway 마이그레이션
```

---

## 라이선스

비공개 프로젝트 (지인 그룹 내부용).
