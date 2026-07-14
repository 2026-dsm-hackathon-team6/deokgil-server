package org.example.deokgilserver.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * FCM(Firebase Cloud Messaging) 발송에 필요한 서비스 계정 자격 증명을 초기화한다.
 * 자격 증명은 Firebase 콘솔 → 프로젝트 설정 → 서비스 계정에서 발급받은 JSON 키 파일이며,
 * 절대 저장소에 커밋하지 않는다 — 파일 경로만 FIREBASE_CREDENTIALS_PATH로 넘긴다.
 */
@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(@Value("${firebase.credentials-path}") String credentialsPath) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try (FileInputStream credentialsStream = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }
}
