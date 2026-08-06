package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberListItemDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberUpdateRequest;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for members. Sub-resources (beneficiaries / documents /
 * medical-record) get their own controllers per vertical-4 separate bullets.
 *
 * <p>Pagination follows the project convention: default page size 20 sorted
 * by {@code enrolledAt} DESC (most recent affiliations first); override
 * with {@code ?page=&size=&sort=}. {@code size=-1} returns everything via
 * the project's {@code UnpagedAwarePageableArgumentResolver}.</p>
 */
@RestController
@RequestMapping("/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final MembersService membersService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_ALL')")
    public ResponseEntity<Page<MemberListItemDto>> list(
            @PageableDefault(size = 20, sort = "enrolledAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(membersService.list(pageable, filter, q));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('MEMBER_VIEW_ALL')")
    public ResponseEntity<List<OptionDto>> options(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(membersService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBER_VIEW_ALL')")
    public ResponseEntity<MemberDetailDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(membersService.getDetail(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MEMBER_CREATE')")
    public ResponseEntity<MemberDetailDto> create(@Valid @RequestBody MemberCreateRequest request) {
        MemberDetailDto created = membersService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBER_UPDATE')")
    public ResponseEntity<MemberDetailDto> update(@PathVariable UUID uuid,
                                                  @Valid @RequestBody MemberUpdateRequest request) {
        return ResponseEntity.ok(membersService.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        membersService.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
