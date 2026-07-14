-- 푸시 알림(FCM) 지원을 위한 스키마 변경
-- User.fcmToken(디바이스 등록 토큰) 필드 추가에 대응한다. NULL 허용 컬럼 추가라
-- 001과 달리 기존 데이터에 영향은 없다.

ALTER TABLE users
    ADD COLUMN fcm_token VARCHAR(255) NULL;
