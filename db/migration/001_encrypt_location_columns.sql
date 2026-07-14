-- 위치 컬럼 암호화 적용을 위한 스키마 변경
-- EncryptedBigDecimalConverter가 위경도를 Base64 인코딩된 AES-GCM 암호문(문자열)으로
-- 저장하므로, 기존 DECIMAL(10,7) 컬럼으로는 더 이상 담을 수 없다.
--
-- ⚠ 실행 전 필독
--   1. 이 스크립트는 자동 실행되지 않는다. 반드시 내용을 검토한 뒤 운영 DB에 직접 적용할 것.
--   2. 반드시 백업을 먼저 뜬 뒤 실행할 것 (아래는 파괴적 변경이다: 컬럼 타입 변경 시
--      MySQL이 기존 숫자 값을 문자열로 그대로 캐스팅해서 남기지만, 그 값은 EncryptedBigDecimalConverter가
--      만든 암호문 형식이 아니므로 애플리케이션이 그 값을 읽으려 하면 복호화에 실패한다).
--   3. 따라서 이미 저장된 좌표가 있다면, 컬럼 타입 변경 전에 애플리케이션 레벨에서
--      "평문 값을 읽어 암호화해서 다시 쓰는" 별도의 1회성 마이그레이션이 필요하다.
--      신규 프로젝트라 아직 저장된 좌표가 없다면 이 단계는 생략 가능하다.
--   4. VARCHAR(255)로 잡은 이유: Base64로 인코딩된 IV(12byte) + AES-GCM 암호문 +
--      태그(16byte)를 합쳐도 좌표 하나(최대 십수 byte 평문) 기준 100자 내외이므로 여유 있게 잡았다.

ALTER TABLE events
    MODIFY COLUMN latitude  VARCHAR(255) NULL,
    MODIFY COLUMN longitude VARCHAR(255) NULL;

ALTER TABLE schedules
    MODIFY COLUMN latitude  VARCHAR(255) NULL,
    MODIFY COLUMN longitude VARCHAR(255) NULL;
