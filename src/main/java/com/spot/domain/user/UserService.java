package com.spot.domain.user;

import com.spot.common.BadRequestException;
import com.spot.common.NotFoundException;
import com.spot.common.StudyDayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User resolveSocialUser(
        AuthProvider provider,
        String providerId,
        String email,
        String naverNickname,
        int defaultGoalMinutes
    ) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
            .map(existing -> {
                existing.updateNaverNickname(naverNickname);
                return existing;
            })
            .orElseGet(() -> userRepository.save(
                User.ofSocial(provider, providerId, email, naverNickname, defaultGoalMinutes)
            ));
    }

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public User updateDefaultGoal(Long userId, int defaultGoalMinutes) {
        User user = getById(userId);
        user.changeDefaultGoalMinutes(defaultGoalMinutes);
        return user;
    }

    @Transactional
    public User updateStudyDayResetHour(Long userId, int studyDayResetHour) {
        int hour = StudyDayService.normalizeResetHour(studyDayResetHour);
        User user = getById(userId);
        user.changeStudyDayResetHour(hour);
        return user;
    }

    @Transactional
    public User updateDisplayName(Long userId, String rawDisplayName) {
        String displayName = validateDisplayName(rawDisplayName);
        User user = getById(userId);
        user.changeDisplayName(displayName);
        return user;
    }

    static String validateDisplayName(String rawDisplayName) {
        if (!StringUtils.hasText(rawDisplayName)) {
            throw new BadRequestException("DISPLAY_NAME_REQUIRED", "표시 이름을 입력해주세요.");
        }
        String displayName = rawDisplayName.trim();
        if (displayName.length() < User.MIN_DISPLAY_NAME_LENGTH
            || displayName.length() > User.MAX_DISPLAY_NAME_LENGTH) {
            throw new BadRequestException(
                "INVALID_DISPLAY_NAME",
                "표시 이름은 " + User.MIN_DISPLAY_NAME_LENGTH + "~"
                    + User.MAX_DISPLAY_NAME_LENGTH + "자여야 합니다."
            );
        }
        return displayName;
    }
}
