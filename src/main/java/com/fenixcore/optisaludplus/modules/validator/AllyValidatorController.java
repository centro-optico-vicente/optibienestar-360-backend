package com.fenixcore.optisaludplus.modules.validator;

import com.fenixcore.optisaludplus.modules.validator.dto.ValidationResultDto;
import com.fenixcore.optisaludplus.modules.validator.service.ValidatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Realtime ally validator. Single endpoint per the vertical-7 spec.
 *
 * <p>The ally portal calls this BEFORE applying any benefit so the operator
 * knows whether the affiliate is solvent at this moment. The endpoint is
 * Redis-cached (60-second TTL) inside {@link ValidatorService}; on cache
 * miss the service walks {@code Person → Member → active Membership} and
 * returns the resolved state. {@link
 * com.fenixcore.optisaludplus.modules.validator.service.ValidatorCacheService}
 * evicts the entry whenever a payment is approved / rejected or a
 * membership flips status — that keeps the latency low and the answer
 * fresh without an external sync mechanism.</p>
 */
@RestController
@RequestMapping("/v1/ally/validate")
@RequiredArgsConstructor
public class AllyValidatorController {

    private final ValidatorService validatorService;

    @GetMapping("/{document}")
    @PreAuthorize("hasAuthority('ALLY_VALIDATE_MEMBER')")
    public ResponseEntity<ValidationResultDto> validate(@PathVariable String document) {
        return ResponseEntity.ok(validatorService.validate(document));
    }
}
