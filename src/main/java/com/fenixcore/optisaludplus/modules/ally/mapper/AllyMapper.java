package com.fenixcore.optisaludplus.modules.ally.mapper;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyAgreementDto;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyListItemDto;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyUserDto;
import com.fenixcore.optisaludplus.modules.ally.dto.PublicAllyListItemDto;
import com.fenixcore.optisaludplus.modules.ally.entity.Ally;
import com.fenixcore.optisaludplus.modules.ally.entity.AllyAgreement;
import com.fenixcore.optisaludplus.modules.ally.entity.AllyService;
import com.fenixcore.optisaludplus.modules.ally.entity.AllyUser;
import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.CityDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.ServiceCategoryDto;
import com.fenixcore.optisaludplus.modules.catalog.entity.AllyType;
import com.fenixcore.optisaludplus.modules.catalog.entity.City;
import com.fenixcore.optisaludplus.modules.catalog.entity.MedicalSpecialty;
import com.fenixcore.optisaludplus.modules.catalog.entity.ServiceCategory;
import com.fenixcore.optisaludplus.modules.catalog.entity.State;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface AllyMapper {

    // ─── Ally → DTOs ───────────────────────────────────────────────────────

    @Mapping(target = "allyTypeUuid", source = "allyType.uuid")
    @Mapping(target = "allyTypeName", source = "allyType.name")
    @Mapping(target = "cityUuid",     source = "city.uuid")
    @Mapping(target = "cityName",     source = "city.name")
    AllyListItemDto toListItem(Ally ally);

    /** Sanitized projection for the public directory. See {@link PublicAllyListItemDto}. */
    @Mapping(target = "allyTypeName", source = "allyType.name")
    @Mapping(target = "cityName",     source = "city.name")
    PublicAllyListItemDto toPublicListItem(Ally ally);

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
    AllyServiceDto toServiceDto(AllyService service);

    // ─── AllyUser → DTO ────────────────────────────────────────────────────

    @Mapping(target = "allyUuid",     source = "ally.uuid")
    @Mapping(target = "userUuid",     source = "user.uuid")
    @Mapping(target = "userEmail",    source = "user.email")
    @Mapping(target = "userFullName", source = "user.person.fullName")
    AllyUserDto toAllyUserDto(AllyUser allyUser);

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

    default int countActive(Collection<? extends BaseEntity> entities) {
        if (entities == null) return 0;
        return (int) entities.stream().filter(BaseEntity::isActive).count();
    }
}
