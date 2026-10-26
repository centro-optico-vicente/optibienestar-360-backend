package com.fenixcore.optibienestar360.modules.ally.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUserDto;
import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyDto;
import com.fenixcore.optibienestar360.modules.ally.dto.UserAllyDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductListItemDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.service.AllyFileNumbers;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProfessionDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProductCategoryDto;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.Profession;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
import com.fenixcore.optibienestar360.modules.catalog.entity.State;
import com.fenixcore.optibienestar360.modules.product.mapper.ProductMapper;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface AllyMapper {

    // ─── Ally → DTOs ───────────────────────────────────────────────────────

    /**
     * Compact list row. {@code city} maps to {@link
     * com.fenixcore.optibienestar360.core.display.DisplayRef} via
     * {@link DisplayRefs}; the serializer flattens it to
     * {@code <rel>_Uuid} + {@code <rel>_Display} and adds the scalar
     * {@code _Display} siblings from the request {@code Locale} (ADR 0014).
     * {@code allyTypeNames} is multi-valued (M:N) so it no longer fits that
     * single-FK convention and is exposed as a plain label list instead.
     */
    @Mapping(target = "allyTypeNames", expression = "java(extractAllyTypeNames(ally.getAllyTypes()))")
    AllyListItemDto toListItem(Ally ally);

    /** Sanitized projection for the public directory. See {@link PublicAllyListItemDto}. */
    @Mapping(target = "allyTypeNames", expression = "java(extractAllyTypeNames(ally.getAllyTypes()))")
    @Mapping(target = "allySegments",  expression = "java(extractAllySegments(ally.getAllyTypes()))")
    @Mapping(target = "cityName",      source = "city.name")
    PublicAllyListItemDto toPublicListItem(Ally ally);

    /**
     * Sanitized projection for the public detail page. Same omission rules
     * as {@link #toPublicListItem} plus filtered sub-lists: profession
     * names + only the public-visible offers
     * ({@code active AND published AND reviewStatus=APPROVED}, active product).
     */
    @Mapping(target = "allyTypeNames",   expression = "java(extractAllyTypeNames(ally.getAllyTypes()))")
    @Mapping(target = "allySegments",   expression = "java(extractAllySegments(ally.getAllyTypes()))")
    @Mapping(target = "cityName",       source = "city.name")
    @Mapping(target = "professionNames", expression = "java(extractProfessionNames(ally.getProfessions()))")
    @Mapping(target = "services",       expression = "java(extractPublicProducts(ally.getProducts(), publicBaseUrl))")
    PublicAllyDetailDto toPublicDetail(Ally ally, @Context String publicBaseUrl);

    @Mapping(target = "productUuid",         source = "product.uuid")
    @Mapping(target = "productType",         source = "product.productType")
    @Mapping(target = "categoryName",        source = "product.productCategory.name")
    @Mapping(target = "name",                source = "product.name")
    @Mapping(target = "description",         source = "product.description")
    @Mapping(target = "requiresAppointment", source = "product.requiresAppointment")
    @Mapping(target = "imageUrl",            expression = "java(imageUrl(offer, publicBaseUrl))")
    PublicAllyProductDto toPublicProductDto(AllyProduct offer, @Context String publicBaseUrl);

    /**
     * Cross-ally public catalog row — see {@link PublicProductListItemDto}.
     * Flattens the product and a compact slice of the parent ally's public
     * identity onto the offer so a catalog result stands on its own.
     */
    @Mapping(target = "productUuid",         source = "product.uuid")
    @Mapping(target = "productType",         source = "product.productType")
    @Mapping(target = "categoryName",        source = "product.productCategory.name")
    @Mapping(target = "name",                source = "product.name")
    @Mapping(target = "description",         source = "product.description")
    @Mapping(target = "requiresAppointment", source = "product.requiresAppointment")
    @Mapping(target = "imageUrl",      expression = "java(imageUrl(offer, publicBaseUrl))")
    @Mapping(target = "allyUuid",      source = "ally.uuid")
    @Mapping(target = "allyName",      source = "ally.name")
    @Mapping(target = "allyTypeNames", expression = "java(extractAllyTypeNames(offer.getAlly().getAllyTypes()))")
    @Mapping(target = "allyCityName",  source = "ally.city.name")
    @Mapping(target = "allyLogoUrl",   source = "ally.logoUrl")
    @Mapping(target = "allyPhone",     source = "ally.phone")
    PublicProductListItemDto toPublicProductListItem(AllyProduct offer, @Context String publicBaseUrl);

    @Mapping(target = "allyTypes",   expression = "java(toAllyTypeDtoList(ally.getAllyTypes()))")
    @Mapping(target = "city",        source = "city")
    @Mapping(target = "professions", source = "professions")
    @Mapping(target = "activeUsersCount",      expression = "java(countActive(ally.getUsers()))")
    @Mapping(target = "activeProductsCount",   expression = "java(countActive(ally.getProducts()))")
    @Mapping(target = "activeAgreementsCount", expression = "java(countActive(ally.getAgreements()))")
    @Mapping(target = "capturedByPromoter",    expression = "java(promoterRef(ally.getCapturedByPromoter()))")
    @Mapping(target = "fileNumber",            expression = "java(fileNumber(ally))")
    AllyDetailDto toDetail(Ally ally);

    // ─── AllyAgreement → DTO ───────────────────────────────────────────────

    @Mapping(target = "allyUuid",     source = "ally.uuid")
    @Mapping(target = "termsVersion", expression = "java(termsVersionRef(agreement.getTermsVersion()))")
    AllyAgreementDto toAgreementDto(AllyAgreement agreement);

    // ─── AllyProduct → DTO ─────────────────────────────────────────────────

    @Mapping(target = "allyUuid",            source = "ally.uuid")
    @Mapping(target = "productUuid",         source = "product.uuid")
    @Mapping(target = "productType",         source = "product.productType")
    @Mapping(target = "exclusive",           expression = "java(offer.getProduct().isExclusive())")
    @Mapping(target = "productCategory",     source = "product.productCategory")
    @Mapping(target = "name",                source = "product.name")
    @Mapping(target = "description",         source = "product.description")
    @Mapping(target = "requiresAppointment", source = "product.requiresAppointment")
    @Mapping(target = "reviewedByUuid",      source = "reviewedBy.uuid")
    @Mapping(target = "imageUrl",            expression = "java(imageUrl(offer, publicBaseUrl))")
    AllyProductDto toAllyProductDto(AllyProduct offer, @Context String publicBaseUrl);

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
    @Mapping(target = "uuid",          source = "ally.uuid")
    @Mapping(target = "name",         source = "ally.name")
    @Mapping(target = "allyTypeNames", expression = "java(extractAllyTypeNames(allyUser.getAlly().getAllyTypes()))")
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
    @Mapping(target = "ally", source = "ally")
    UserAllyDto toUserAllyDto(AllyUser allyUser);

    // ─── Nested catalog DTOs (default methods consumed by the generated impl) ─

    default AllyTypeDto toAllyTypeDto(AllyType allyType) {
        if (allyType == null) return null;
        return new AllyTypeDto(allyType.getUuid(), allyType.getCode(),
                allyType.getName(), allyType.getDescription(),
                allyType.getSegment(), allyType.getProfessionalArea(), allyType.isActive());
    }

    default CityDto toCityDto(City city) {
        if (city == null) return null;
        State state = city.getState();
        return new CityDto(
                city.getUuid(),
                city.getName(),
                state == null ? null : DisplayRef.of(state.getUuid(), state.getCode(), state.getName()),
                city.isActive()
        );
    }

    default ProfessionDto toProfessionDto(Profession ms) {
        if (ms == null) return null;
        return new ProfessionDto(ms.getUuid(), ms.getCode(),
                ms.getName(), ms.getDescription(), ms.isActive());
    }

    default ProductCategoryDto toProductCategoryDto(ProductCategory sc) {
        if (sc == null) return null;
        return new ProductCategoryDto(sc.getUuid(), sc.getCode(),
                sc.getName(), sc.getDescription(), sc.isActive());
    }

    default List<ProfessionDto> toProfessionDtoList(Set<Profession> set) {
        if (set == null) return List.of();
        return set.stream().map(this::toProfessionDto).toList();
    }

    default List<AllyTypeDto> toAllyTypeDtoList(Set<AllyType> set) {
        if (set == null) return List.of();
        return set.stream().map(this::toAllyTypeDto).toList();
    }

    // ─── Agreement file + FK refs (V179) ────────────────────────────────────

    /** Display date of a terms version's {@code validFrom} (ADR 0010 zone, ADR 0022 format). */
    DateTimeFormatter TERMS_VERSION_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            .withZone(ZoneId.of("America/Caracas"));

    /** Agreement file number "[CÓDIGO DE ASESOR]-[Nº DE ALIADO]" — see {@link AllyFileNumbers#of}. */
    default String fileNumber(Ally ally) {
        return AllyFileNumbers.of(ally);
    }

    /** {@code capturedByPromoter_Code} carries the referral code (Promoter has no generic {@code getCode()}). */
    default DisplayRef promoterRef(Promoter promoter) {
        return promoter == null ? null
                : DisplayRef.of(promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName());
    }

    /** Labelled "&lt;title&gt; (&lt;valid-from dd-MM-yyyy&gt;)" so two versions with the same title stay distinguishable. */
    default DisplayRef termsVersionRef(TermsVersion version) {
        if (version == null) return null;
        String label = version.getValidFrom() == null ? version.getTitle()
                : version.getTitle() + " (" + TERMS_VERSION_DATE.format(version.getValidFrom()) + ")";
        return DisplayRef.of(version.getUuid(), null, label);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    /**
     * Derives the public catalog image URL from the persisted key —
     * {@code publicBaseUrl + imageKey} — never the other way around, so a
     * change of public domain never requires a data migration (spec §5).
     * {@code null} until the image is explicitly published.
     */
    default String imageUrl(AllyProduct offer, String publicBaseUrl) {
        return ProductMapper.imageUrl(offer.getProduct(), publicBaseUrl);
    }

    default int countActive(Collection<? extends BaseEntity> entities) {
        if (entities == null) return 0;
        return (int) entities.stream().filter(BaseEntity::isActive).count();
    }

    default List<String> extractProfessionNames(Set<Profession> professions) {
        if (professions == null) return List.of();
        return professions.stream()
                .filter(Profession::isActive)
                .map(Profession::getName)
                .toList();
    }

    /** Distinct segment codes ({@code B2B} / {@code B2P}) of the active types, in a stable order. */
    default List<String> extractAllySegments(Set<AllyType> allyTypes) {
        if (allyTypes == null) return List.of();
        return allyTypes.stream()
                .filter(AllyType::isActive)
                .map(AllyType::getSegment)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .map(Enum::name)
                .toList();
    }

    default List<String> extractAllyTypeNames(Set<AllyType> allyTypes) {
        if (allyTypes == null) return List.of();
        return allyTypes.stream()
                .filter(AllyType::isActive)
                .map(AllyType::getName)
                .toList();
    }

    /**
     * Filters offers to only those visible publicly — active + published +
     * APPROVED, over an active product. Anything in PROPOSED / IN_REVIEW /
     * REJECTED / REMOVED, unpublished, or soft-deleted is dropped before the
     * DTOs are built so anonymous viewers never see them.
     */
    default List<PublicAllyProductDto> extractPublicProducts(List<AllyProduct> offers, String publicBaseUrl) {
        if (offers == null) return List.of();
        return offers.stream()
                .filter(AllyProduct::isActive)
                .filter(AllyProduct::isPublished)
                .filter(o -> o.getReviewStatus() == AllyProduct.ReviewStatus.APPROVED)
                .filter(o -> o.getProduct().isActive())
                .map(o -> toPublicProductDto(o, publicBaseUrl))
                .toList();
    }
}
