# Backend Lab

Java/Spring Boot 백엔드 기술을 작은 기능 단위로 직접 구현하고 검증하는 연습 프로젝트입니다.

단순히 예제 코드를 복사하는 것이 아니라 다음 순서로 학습하는 것을 목표로 합니다.

1. 요구사항을 이해한다.
2. 직접 설계하고 구현한다.
3. 테스트로 정상 동작과 예외 상황을 검증한다.
4. 동시성·성능·장애 상황까지 단계적으로 확장한다.
5. 구현 이유와 선택한 방법의 장단점을 설명할 수 있게 정리한다.

## 기술 스택

- Java 17
- Spring Boot 3.x
- Spring Web
- Spring Data JPA / Hibernate
- MySQL
- Gradle
- JUnit 5 / AssertJ
- Lombok
- Bean Validation

추후 학습 진행에 따라 Redis, Kafka, Spring Batch, Docker 등을 추가합니다.

## 프로젝트 정보

```text
Name: backend-lab
Artifact: backend-lab
Group: com.dohyun
Package: com.dohyun.backendlab
```

## 패키지 구조

```text
com.dohyun.backendlab
├── payment       # 멱등성, 트랜잭션, 결제 취소
├── inventory     # 동시성, 비관적 락, 낙관적 락
├── order         # JPA 연관관계, N+1, 조회 성능
├── auth          # Spring Security, JWT, 인증·인가
└── common        # 공통 예외 처리와 API 응답
```

각 주제는 처음부터 복잡하게 연결하지 않고 독립적으로 구현한 뒤, 필요할 때 도메인 사이의 관계를 추가합니다.

## 학습 로드맵

### 1. Payment

- 결제 요청 멱등성
- 트랜잭션 경계
- 결제 상태 변경
- 결제 취소
- 동시 요청 처리

### 2. Inventory

- 재고 차감
- Lost Update 재현
- 비관적 락과 낙관적 락 비교
- 재시도 처리

### 3. Order

- `Order`와 `OrderItem` 연관관계
- 지연 로딩
- N+1 문제 재현
- Fetch Join과 EntityGraph 비교

### 4. Auth

- 회원 가입과 로그인
- BCrypt 비밀번호 암호화
- JWT 발급과 검증
- Spring Security 인증·인가

### 5. Infrastructure

- Redis 캐시와 분산 락
- Kafka 이벤트 발행·소비
- Spring Batch
- Docker 기반 실행 환경

---

# 첫 번째 실습: 결제 요청 멱등성

## 1. 배경

사용자가 결제 버튼을 여러 번 누르거나 네트워크 문제로 동일한 요청이 재전송될 수 있습니다.

동일한 결제 요청이 두 번 이상 전달되더라도 실제 결제 데이터는 한 건만 생성되어야 합니다. 이를 위해 클라이언트가 전달한 `Idempotency-Key`를 기준으로 동일 요청인지 판단합니다.

> 데이터베이스 PK는 저장 후 생성되므로 재요청 여부를 판단하는 값으로 사용할 수 없습니다. 두 요청에 공통으로 포함되는 멱등성 키가 필요합니다.

## 2. API 요구사항

### 결제 요청

```http
POST /payments
Content-Type: application/json
Idempotency-Key: order-100

{
  "amount": 10000
}
```

### 요청값

| 값 | 위치 | 필수 | 조건 |
| --- | --- | --- | --- |
| `Idempotency-Key` | Header | O | 비어 있을 수 없음 |
| `amount` | Body | O | 0보다 커야 함 |

### 응답 예시

```json
{
  "paymentId": 1,
  "idempotencyKey": "order-100",
  "amount": 10000,
  "status": "COMPLETED"
}
```

## 3. 처리 규칙

### 최초 요청

- 전달받은 멱등성 키로 저장된 결제가 없으면 새로운 결제를 생성합니다.
- 결제 상태는 우선 `COMPLETED`로 저장합니다.
- 생성된 결제 정보를 반환합니다.

### 동일 요청 재전송

- 멱등성 키와 금액이 모두 같으면 새로운 결제를 만들지 않습니다.
- 기존 결제 정보를 그대로 반환합니다.
- 데이터베이스에는 한 건만 존재해야 합니다.

### 같은 키에 다른 금액 요청

- 기존 결제와 멱등성 키는 같지만 금액이 다르면 잘못된 요청입니다.
- 예외를 발생시키고 기존 결제는 변경하지 않습니다.

예시:

```text
기존 요청: order-100 / 10,000원
재요청:   order-100 / 20,000원
결과:     요청 거절
```

## 4. 데이터베이스 요구사항

```sql
CREATE TABLE payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_payment_idempotency_key
        UNIQUE (idempotency_key)
);
```

`idempotency_key`에는 반드시 유니크 제약조건을 설정합니다. 애플리케이션에서 먼저 조회하더라도 두 요청이 동시에 들어오면 모두 조회 결과가 없을 수 있기 때문에, 데이터베이스가 중복 저장을 최종적으로 차단해야 합니다.

## 5. 도메인 요구사항

### Payment

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | `Long` | 데이터베이스 PK |
| `idempotencyKey` | `String` | 중복 결제 방지 키 |
| `amount` | `Long` | 결제 금액 |
| `status` | `PaymentStatus` | 결제 상태 |

### PaymentStatus

첫 단계에서는 아래 상태만 사용합니다.

```java
COMPLETED
```

결제 취소 실습을 진행할 때 `CANCELED`를 추가합니다.

## 6. 필요한 클래스

```text
payment
├── controller
│   └── PaymentController
├── service
│   └── PaymentService
├── repository
│   └── PaymentRepository
├── domain
│   ├── Payment
│   └── PaymentStatus
└── dto
    ├── PaymentRequest
    └── PaymentResponse
```

## 7. 서비스 동작

```java
public PaymentResponse pay(String idempotencyKey, Long amount)
```

처리 순서:

```text
1. 입력값을 검증한다.
2. idempotencyKey로 기존 결제를 조회한다.
3. 기존 결제가 있다.
   ├── 금액도 같다: 기존 결제를 반환한다.
   └── 금액이 다르다: 예외를 발생시킨다.
4. 기존 결제가 없다: 새로운 결제를 저장하고 반환한다.
```

## 8. 필수 테스트

### 기본 기능

- 최초 요청 시 결제가 저장된다.
- 저장된 결제의 상태는 `COMPLETED`다.
- 같은 멱등성 키와 금액으로 재요청하면 기존 결제가 반환된다.
- 동일 요청을 두 번 보내도 결제 데이터는 한 건만 존재한다.
- 같은 멱등성 키에 다른 금액을 요청하면 예외가 발생한다.

### 입력 검증

- 멱등성 키가 비어 있으면 요청을 거절한다.
- 결제 금액이 `null`이면 요청을 거절한다.
- 결제 금액이 0 이하이면 요청을 거절한다.

### 동시성 테스트

기본 기능이 모두 완성된 다음 진행합니다.

- 같은 멱등성 키로 여러 요청을 동시에 보낸다.
- 최종적으로 결제 데이터가 한 건만 저장되는지 확인한다.
- 유니크 제약조건 위반이 발생했을 때 기존 결제를 안전하게 반환하도록 개선한다.

## 9. 구현 순서

1. Spring Boot 프로젝트 생성
2. MySQL 연결
3. `PaymentStatus` 작성
4. `Payment` 엔티티 작성
5. `PaymentRepository` 작성
6. `PaymentService.pay()` 작성
7. 요청·응답 DTO 작성
8. `PaymentController` 작성
9. 기본 기능 테스트 작성
10. 동시 요청 문제 재현 및 개선

## 10. 첫 번째 완료 기준

아래 항목을 모두 만족하면 결제 멱등성 기본 단계가 완료된 것입니다.

- `POST /payments` 호출이 성공한다.
- 최초 요청에서 결제가 한 건 저장된다.
- 같은 요청을 다시 보내도 데이터가 추가되지 않는다.
- 같은 키에 다른 금액을 보내면 예외 응답이 내려온다.
- 주요 동작을 테스트 코드로 검증한다.
- PK가 아닌 멱등성 키가 필요한 이유를 설명할 수 있다.

## 학습 원칙

- 처음부터 정답 코드를 복사하지 않습니다.
- 한 단계씩 직접 구현하고 테스트 결과를 확인합니다.
- 기능이 동작한 뒤 리팩터링합니다.
- 새로운 기술은 현재 문제를 해결할 이유가 생겼을 때 추가합니다.
- 각 실습이 끝나면 문제, 선택한 해결 방법, 결과와 한계를 README에 기록합니다.
