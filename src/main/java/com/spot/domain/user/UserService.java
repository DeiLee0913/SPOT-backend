package com.spot.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        String nickname,
        int defaultGoalMinutes
    ) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
            .orElseGet(() -> userRepository.save(
                User.ofSocial(provider, providerId, email, nickname, defaultGoalMinutes)
            ));
    }

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));
    }

    @Transactional
    public User updateDefaultGoal(Long userId, int defaultGoalMinutes) {
        User user = getById(userId);
        user.changeDefaultGoalMinutes(defaultGoalMinutes);
        return user;
    }
}
