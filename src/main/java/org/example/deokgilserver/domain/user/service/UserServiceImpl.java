package org.example.deokgilserver.domain.user.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.storage.S3PresignedUploadService;
import org.example.deokgilserver.domain.auth.repository.RefreshTokenRepository;
import org.example.deokgilserver.domain.checklist.repository.ChecklistRepository;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.eventrecord.repository.EventRecordRepository;
import org.example.deokgilserver.domain.notification.repository.NotificationRepository;
import org.example.deokgilserver.domain.schedule.repository.ScheduleRepository;
import org.example.deokgilserver.domain.user.domain.User;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateFcmTokenRequest;
import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateProfileRequest;
import org.example.deokgilserver.domain.user.presentation.dto.response.PresignedProfileImageUploadResponse;
import org.example.deokgilserver.domain.user.presentation.dto.response.UserResponse;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final EventRecordRepository eventRecordRepository;
    private final NotificationRepository notificationRepository;
    private final ScheduleRepository scheduleRepository;
    private final ChecklistRepository checklistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final S3PresignedUploadService s3PresignedUploadService;

    public UserServiceImpl(
            UserRepository userRepository,
            EventRepository eventRepository,
            EventRecordRepository eventRecordRepository,
            NotificationRepository notificationRepository,
            ScheduleRepository scheduleRepository,
            ChecklistRepository checklistRepository,
            RefreshTokenRepository refreshTokenRepository,
            S3PresignedUploadService s3PresignedUploadService
    ) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.eventRecordRepository = eventRecordRepository;
        this.notificationRepository = notificationRepository;
        this.scheduleRepository = scheduleRepository;
        this.checklistRepository = checklistRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.s3PresignedUploadService = s3PresignedUploadService;
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getActiveUser(userId);
        String previousProfileImage = user.getProfileImage();

        user.updateProfile(request.nickname(), request.profileImage());

        // 새로 저장된 값이 이전 값과 다를 때만 정리한다 - 같은 URL을 재전송하는(예: 닉네임만
        // 바꾸는) 요청까지 매번 삭제를 시도하지 않기 위함이다. 우리 버킷 소속이 아닌 URL(예:
        // Google 프로필 사진)은 S3PresignedUploadService 내부에서 조용히 무시된다.
        if (previousProfileImage != null && !previousProfileImage.equals(request.profileImage())) {
            s3PresignedUploadService.deleteIfOwnedByBucket(previousProfileImage);
        }

        log.info("프로필 수정 완료: userId={}", userId);
        return UserResponse.from(user);
    }

    @Override
    public PresignedProfileImageUploadResponse createProfileImageUploadUrl(UUID userId, String contentType) {
        getActiveUser(userId);
        S3PresignedUploadService.PresignedUpload upload = s3PresignedUploadService.createPresignedUpload(
                "profile-images/" + userId + "/", contentType);
        return new PresignedProfileImageUploadResponse(upload.uploadUrl(), upload.imageUrl());
    }

    @Override
    public PresignedProfileImageUploadResponse createProfileImageUploadUrlForSignup(String contentType) {
        S3PresignedUploadService.PresignedUpload upload = s3PresignedUploadService.createPresignedUpload(
                "profile-images/pending/", contentType);
        return new PresignedProfileImageUploadResponse(upload.uploadUrl(), upload.imageUrl());
    }

    /**
     * 탈퇴 즉시 물리 삭제(hard delete)한다. User/Event/EventRecord는 FK가 nullable=false라
     * 자식부터 순서대로 지워야 제약 위반이 나지 않는다: 행사별 Notification/Schedule/Checklist
     * → 사용자의 EventRecord → Event → (Redis) RefreshToken → User. 이 순서를 바꾸면 FK
     * 제약 위반으로 트랜잭션이 롤백된다.
     */
    @Override
    @Transactional
    public void withdraw(UUID userId) {
        User user = getActiveUser(userId);

        List<Event> events = eventRepository.findByUserId(userId);
        for (Event event : events) {
            notificationRepository.deleteByEventId(event.getId());
            scheduleRepository.deleteByEventId(event.getId());
            checklistRepository.deleteByEventId(event.getId());
        }
        eventRecordRepository.deleteAll(eventRecordRepository.findByUserId(userId));
        eventRepository.deleteAll(events);

        refreshTokenRepository.delete(userId);
        userRepository.delete(user);
        log.info("회원 탈퇴 완료: userId={}, deletedEventCount={}", userId, events.size());
    }

    @Override
    @Transactional
    public void updateFcmToken(UUID userId, UpdateFcmTokenRequest request) {
        User user = getActiveUser(userId);
        user.updateFcmToken(request.fcmToken());
    }

    private User getActiveUser(UUID userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
