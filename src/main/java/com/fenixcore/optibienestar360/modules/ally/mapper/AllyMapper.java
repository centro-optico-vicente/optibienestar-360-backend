package com.fenixcore.optibienestar360.modules.ally.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUserDto;
import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyDto;
import com.fenixcore.optibienestar360.modules.ally.dto.UserAllyDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicServiceListItemDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.MedicalSpecialtyDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.ServiceCategoryDto;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.MedicalSpecialty;
import com.fenixcore.optibienestar360.modules.catalog.entity.ServiceCategory;
import com.fenixcore.optibienestar360.modules.catalog.entity.State;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface AllyMapper {

    // ─── Ally → DTOs ───────────────────────────────────────────────────────

    /**
     * Compact list row. {@code allyType} / {@code city} map to {@link
     * com.fenixcore.optibienestar360.core.display.DisplayRef} via
     * {@link DisplayRefs}; the serializer flattens them to
     * {@code <rel>_Uuid} + {@code <rel>_Display} and adds the scalar
     * {@code _Display} siblings from the request {@code Locale} (ADR 0014).
     */
    AllyListItemDto toListItem(Ally ally);

    /** Sanitized projection for the public directory. See {@link PublicAllyListItemDto}. */
    @Mapping(target = "allyTypeName", source = "allyType.name")
    @Mapping(target = "cityName",     source = "city.name")
    PublicAllyListItemDto toPublicListItem(Ally ally);

    /**
     * Sanitized projection for the public detail page. Same omission rules
     * as {@link #toPublicListItem} plus filtered sub-lists: specialty
     * names + only the public-visible services
     * ({@code active AND published AND reviewStatus=APPROVED}).
     */
    @Mapping(target = "allyTypeName",   source = "allyType.name")
    @Mapping(target = "cityName",       source = "city.name")
    @Mapping(target = "specialtyNames", expression = "java(extractSpecialtyNames(ally.getSpecialties()))")
    @Mapping(target = "services",       expression = "java(extractPublicServices(ally.getServices()))")
    PublicAllyDetailDto toPublicDetail(Ally ally);

    @Mapping(target = "categoryName", source = "serviceCategory.name")
    PublicAllyServiceDto toPublicServiceDto(AllyService service);

    /**
     * Cross-ally public catalog row — see {@link PublicServiceListItemDto}.
     * Flattens a compact slice of the parent ally's public identity onto the
     * service so a {@code GET /v1/public/services} result stands on its own.
     */
    @Mapping(target = "categoryName", source = "serviceCategory.name")
    @Mapping(target = "imageUrl",     expression = "java(imageUrl(service, publicBaseUrl))")
    @Mapping(target = "allyUuid",     source = "ally.uuid")
    @Mapping(target = "allyName",     source = "ally.name")
    @Mapping(target = "allyTypeName", source = "ally.allyType.name")
    @Mapping(target = "allyCityName", source = "ally.city.name")
    @Mapping(target = "allyLogoUrl",  source = "ally.logoUrl")
    @Mapping(target = "allyPhone",    source = "ally.phone")
    PublicServiceListItemDto toPublicServiceListItem(AllyService service, @Context String publicBaseUrl);

    @Mapping(target = "allyType",    source = "allyType")
    @Mapping(target = "city",        source = "city")
    @Mapping(target = "specialties", source = "specialties")
    @Mapping(target = "activeUsersCount",      expression = "java(countActive(ally.getUsers()))")
    @Mapping(target = "activeServicesCount",   expression = "java(countActive(ally.getServices()))")
    @Mapping(target = "activeAgreementsCount", expression = "java(countActive(ally.getAgreements()))")
    AllyDetailDto toDetail(Ally ally);

    // ─── AllyAgreement → DTO ───────────────────────────────────────────────

    @Mapping(target = "allyUuid", source = "ally.uuid")
    AllyAgreementDto toAgreementDto(AllyAgreement agreement);

    // ─── AllyService → DTO ─────────────────────────────────────────────────

    @Mapping(target = "allyUuid",        source = "ally.uuid")
    @Mapping(target = "serviceCategory", source = "serviceCategory")
    @Mapping(target = "reviewedByUuid",  source = "reviewedBy.uuid")
    @Mapping(target = "imageUrl",        expression = "java(imageUrl(service, publicBaseUrl))")
    AllyServiceDto toServiceDto(AllyService service, @Context String publicBaseUrl);

    // ─── AllyUser → DTO ────────────────────────────────────────────────────

    @Mapping(target = "allyUuid",     source = "ally.uuid")
    @Mapping(target = "userUuid",     source = "user.uuid")
    @Mapping(target = "userEmail",    source = "user.email")
    @Mapping(target = "userFullName", source = "user.person.fullName")
    AllyUserDto toAllyUserDto(AllyUser allyUser);

    /**
     * Self-service projection for {@code GET /v1/me/allies}. Flips the
     * perspective of {@link #toAllyUserDto}: the identity fields come from
     * the parent ally (the portal already knows who the caller is), while
     * {@code allyRole} / {@code primary} / {@code joinedAt} stay on the pivot.
     * {@code uuid} is deliberately the ally's — see {@link MyAllyDto}.
     */
    @Mapping(target = "uuid",         source = "ally.uuid")
    @Mapping(target = "name",         source = "ally.name")
    @Mapping(target = "allyTypeUuid", source = "ally.allyType.uuid")
    @Mapping(target = "allyTypeName", source = "ally.allyType.name")
    @Mapping(target = "logoUrl",      source = "ally.logoUrl")
    @Mapping(target = "phone",        source = "ally.phone")
    MyAllyDto toMyAllyDto(AllyUser allyUser);

    /**
     * Admin reverse-lookup projection for {@code GET
     * /v1/admin/users/{userUuid}/allies}. Same perspective flip as {@link
     * #toMyAllyDto} (identity from the ally, membership from the pivot) but
     * keeps {@code active} explicit since the admin caller isn't implicitly
     * scoped to "only my active memberships" the way {@code /v1/me/allies}
     * is.
     */
    @Mapping(target = "allyUuid",  source = "ally.uuid")
    @Mapping(target = "allyName",  source = "ally.name")
    UserAllyDto toUserAllyDto(AllyUser allyUser);

    // ─── Nested catalog DTOs (default methods consumed by the generated impl) ─

    default AllyTypeDto toAllyTypeDto(AllyType allyType) {
        if (allyType == null) return null;
        return new AllyTypeDto(allyType.getUuid(), allyType.getCode(),
                allyType.getName(), allyType.getDescription(), allyType.isActive());
    }

    default CityDto toCityDto(City city) {
        if (city == null) return null;
        State state = city.getState();
        return new CityDto(
                city.getUuid(),
                city.getName(),
                state != null ? state.getUuid() : null,
                state != null ? state.getCode() : null,
                city.isActive()
        );
    }

    default MedicalSpecialtyDto toMedicalSpecialtyDto(MedicalSpecialty ms) {
        if (ms == null) return null;
        return new MedicalSpecialtyDto(ms.getUuid(), ms.getCode(),
                ms.getName(), ms.getDescription(), ms.isActive());
    }

    default ServiceCategoryDto toServiceCategoryDto(ServiceCategory sc) {
        if (sc == null) return null;
        return new ServiceCategoryDto(sc.getUuid(), sc.getCode(),
                sc.getName(), sc.getDescription(), sc.isActive());
    }

    default List<MedicalSpecialtyDto> toMedicalSpecialtyDtoList(Set<MedicalSpecialty> set) {
        if (set == null) return List.of();
        return set.stream().map(this::toMedicalSpecialtyDto).toList();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    /**
     * Derives the public catalog image URL from the persisted key —
     * {@code publicBaseUrl + imageKey} — never the other way around, so a
     * change of public domain never requires a data migration (spec §5).
     * {@code null} until the image is explicitly published.
     */
    default String imageUrl(AllyService service, String publicBaseUrl) {
        String imageKey = service.getImageKey();
        if (imageKey == null || publicBaseUrl == null || publicBaseUrl.isBlank()) return null;
        return publicBaseUrl.endsWith("/") ? publicBaseUrl + imageKey : publicBaseUrl + "/" + imageKey;
    }

    default int countActive(Collection<? extends BaseEntity> entities) {
        if (entities == null) return 0;
        return (int) entities.stream().filter(BaseEntity::isActive).count();
    }

    default List<String> extractSpecialtyNames(Set<MedicalSpecialty> specialties) {
        if (specialties == null) return List.of();
        return specialties.stream()
                .filter(MedicalSpecialty::isActive)
                .map(MedicalSpecialty::getName)
                .toList();
    }

    /**
     * Filters services to only those visible publicly — active + published +
     * APPROVED. Anything in PROPOSED / IN_REVIEW / REJECTED / REMOVED, or
     * unpublished, or soft-deleted is dropped before the DTOs are built so
     * anonymous viewers never see them.
     */
    default List<PublicAllyServiceDto> extractPublicServices(List<AllyService> services) {
        if (services == null) return List.of();
        return services.stream()
                .filter(AllyService::isActive)
                .filter(AllyService::isPublished)
                .filter(s -> s.getReviewStatus() == AllyService.ReviewStatus.APPROVED)
                .map(this::toPublicServiceDto)
                .toList();
    }
}
