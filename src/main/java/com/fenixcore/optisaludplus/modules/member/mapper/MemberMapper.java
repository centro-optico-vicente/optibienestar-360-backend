package com.fenixcore.optisaludplus.modules.member.mapper;

import com.fenixcore.optisaludplus.modules.catalog.dto.CityDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.GenderDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MaritalStatusDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.OccupationDto;
import com.fenixcore.optisaludplus.modules.catalog.entity.City;
import com.fenixcore.optisaludplus.modules.catalog.entity.Gender;
import com.fenixcore.optisaludplus.modules.catalog.entity.MaritalStatus;
import com.fenixcore.optisaludplus.modules.catalog.entity.Occupation;
import com.fenixcore.optisaludplus.modules.catalog.entity.State;
import com.fenixcore.optisaludplus.modules.member.dto.BeneficiaryDto;
import com.fenixcore.optisaludplus.modules.member.dto.MedicalRecordDto;
import com.fenixcore.optisaludplus.modules.member.dto.MemberDetailDto;
import com.fenixcore.optisaludplus.modules.member.dto.MemberListItemDto;
import com.fenixcore.optisaludplus.modules.member.entity.Beneficiary;
import com.fenixcore.optisaludplus.modules.member.entity.MedicalRecord;
import com.fenixcore.optisaludplus.modules.member.entity.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    // ─── Member → DTOs ──────────────────────────────────────────────────────

    @Mapping(target = "fullName",       source = "person.fullName")
    @Mapping(target = "documentType",   source = "person.documentType")
    @Mapping(target = "documentNumber", source = "person.documentNumber")
    @Mapping(target = "phone",          source = "person.phone")
    @Mapping(target = "cityName",       source = "person.city.name")
    MemberListItemDto toListItem(Member member);

    @Mapping(target = "personUuid",         source = "person.uuid")
    @Mapping(target = "firstName",          source = "person.firstName")
    @Mapping(target = "middleName",         source = "person.middleName")
    @Mapping(target = "lastName",           source = "person.lastName")
    @Mapping(target = "secondLastName",     source = "person.secondLastName")
    @Mapping(target = "fullName",           source = "person.fullName")
    @Mapping(target = "documentType",       source = "person.documentType")
    @Mapping(target = "documentNumber",     source = "person.documentNumber")
    @Mapping(target = "taxDocumentType",    source = "person.taxDocumentType")
    @Mapping(target = "taxDocumentNumber",  source = "person.taxDocumentNumber")
    @Mapping(target = "birthDate",          source = "person.birthDate")
    @Mapping(target = "gender",             source = "person.gender")
    @Mapping(target = "maritalStatus",      source = "person.maritalStatus")
    @Mapping(target = "phone",              source = "person.phone")
    @Mapping(target = "email",              source = "person.email")
    @Mapping(target = "locale",             source = "person.locale")
    @Mapping(target = "address",            source = "person.address")
    @Mapping(target = "city",               source = "person.city")
    @Mapping(target = "occupation",         source = "occupation")
    @Mapping(target = "activeBeneficiariesCount", ignore = true)
    @Mapping(target = "activeDocumentsCount",     ignore = true)
    @Mapping(target = "hasMedicalRecord",         ignore = true)
    MemberDetailDto toDetail(Member member);

    // ─── Beneficiary → DTO ──────────────────────────────────────────────────

    @Mapping(target = "memberUuid",       source = "member.uuid")
    @Mapping(target = "personUuid",       source = "person.uuid")
    @Mapping(target = "firstName",        source = "person.firstName")
    @Mapping(target = "middleName",       source = "person.middleName")
    @Mapping(target = "lastName",         source = "person.lastName")
    @Mapping(target = "secondLastName",   source = "person.secondLastName")
    @Mapping(target = "fullName",         source = "person.fullName")
    @Mapping(target = "documentType",     source = "person.documentType")
    @Mapping(target = "documentNumber",   source = "person.documentNumber")
    @Mapping(target = "birthDate",        source = "person.birthDate")
    @Mapping(target = "phone",            source = "person.phone")
    @Mapping(target = "email",            source = "person.email")
    BeneficiaryDto toBeneficiaryDto(Beneficiary beneficiary);

    // ─── MedicalRecord → DTO ────────────────────────────────────────────────

    @Mapping(target = "personUuid", source = "person.uuid")
    MedicalRecordDto toMedicalRecordDto(MedicalRecord record);

    // ─── Nested catalog DTOs (default methods, mirror AllyMapper pattern) ─

    default GenderDto toGenderDto(Gender g) {
        if (g == null) return null;
        return new GenderDto(g.getUuid(), g.getCode(), g.getName(), g.isActive());
    }

    default MaritalStatusDto toMaritalStatusDto(MaritalStatus ms) {
        if (ms == null) return null;
        return new MaritalStatusDto(ms.getUuid(), ms.getCode(), ms.getName(), ms.isActive());
    }

    default OccupationDto toOccupationDto(Occupation o) {
        if (o == null) return null;
        return new OccupationDto(o.getUuid(), o.getName(), o.getDescription(), o.isActive());
    }

    default CityDto toCityDto(City c) {
        if (c == null) return null;
        State state = c.getState();
        return new CityDto(
                c.getUuid(),
                c.getName(),
                state != null ? state.getUuid() : null,
                state != null ? state.getCode() : null,
                c.isActive()
        );
    }
}
