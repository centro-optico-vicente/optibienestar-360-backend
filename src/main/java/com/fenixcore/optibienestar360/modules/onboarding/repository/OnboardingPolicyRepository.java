package com.fenixcore.optibienestar360.modules.onboarding.repository;

import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional(readOnly = true)
public interface OnboardingPolicyRepository extends JpaRepository<OnboardingPolicy, Long> {

    Optional<OnboardingPolicy> findFirstByActiveTrue();
}
