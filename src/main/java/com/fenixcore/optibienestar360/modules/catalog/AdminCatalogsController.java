package com.fenixcore.optibienestar360.modules.catalog;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProfessionCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProfessionDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProfessionUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.ServiceCategoryCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.ServiceCategoryDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.ServiceCategoryUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.DocumentTypeCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.DocumentTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.DocumentTypeUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.OccupationCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.OccupationDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.OccupationUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.PromoterTypeCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.PromoterTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.PromoterTypeUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.StateCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.StateDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.StateUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.catalog.service.AllyTypeService;
import com.fenixcore.optibienestar360.modules.catalog.service.CityService;
import com.fenixcore.optibienestar360.modules.catalog.service.CountryService;
import com.fenixcore.optibienestar360.modules.catalog.service.DocumentTypeService;
import com.fenixcore.optibienestar360.modules.catalog.service.GenderService;
import com.fenixcore.optibienestar360.modules.catalog.service.MaritalStatusService;
import com.fenixcore.optibienestar360.modules.catalog.service.ProfessionService;
import com.fenixcore.optibienestar360.modules.catalog.service.OccupationService;
import com.fenixcore.optibienestar360.modules.catalog.service.PromoterTypeService;
import com.fenixcore.optibienestar360.modules.catalog.service.ServiceCategoryService;
import com.fenixcore.optibienestar360.modules.catalog.service.StateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD over catalog tables.
 *
 * <p>Authorization (V78): each catalog has its own 4 permissions —
 * {@code <ENTITY>_VIEW_ALL} / {@code _CREATE} / {@code _UPDATE} / {@code _DELETE}
 * (e.g. {@code COUNTRY_VIEW_ALL}, {@code COUNTRY_CREATE}). Fully granular per
 * catalog and per action, so a role can be given read-only access to one
 * catalog and full CRUD on another.
 */
@RestController
@RequestMapping("/v1/admin/catalogs")
@RequiredArgsConstructor
public class AdminCatalogsController {

    private static final String COUNTRY_VIEW   = "hasAuthority('COUNTRY_VIEW_ALL')";
    private static final String COUNTRY_CREATE = "hasAuthority('COUNTRY_CREATE')";
    private static final String COUNTRY_UPDATE = "hasAuthority('COUNTRY_UPDATE')";
    private static final String COUNTRY_DELETE = "hasAuthority('COUNTRY_DELETE')";

    private static final String STATE_VIEW   = "hasAuthority('STATE_VIEW_ALL')";
    private static final String STATE_CREATE = "hasAuthority('STATE_CREATE')";
    private static final String STATE_UPDATE = "hasAuthority('STATE_UPDATE')";
    private static final String STATE_DELETE = "hasAuthority('STATE_DELETE')";

    private static final String CITY_VIEW   = "hasAuthority('CITY_VIEW_ALL')";
    private static final String CITY_CREATE = "hasAuthority('CITY_CREATE')";
    private static final String CITY_UPDATE = "hasAuthority('CITY_UPDATE')";
    private static final String CITY_DELETE = "hasAuthority('CITY_DELETE')";

    private static final String GENDER_VIEW   = "hasAuthority('GENDER_VIEW_ALL')";
    private static final String GENDER_CREATE = "hasAuthority('GENDER_CREATE')";
    private static final String GENDER_UPDATE = "hasAuthority('GENDER_UPDATE')";
    private static final String GENDER_DELETE = "hasAuthority('GENDER_DELETE')";

    private static final String DOCUMENT_TYPE_VIEW   = "hasAuthority('DOCUMENT_TYPE_VIEW_ALL')";
    private static final String DOCUMENT_TYPE_CREATE = "hasAuthority('DOCUMENT_TYPE_CREATE')";
    private static final String DOCUMENT_TYPE_UPDATE = "hasAuthority('DOCUMENT_TYPE_UPDATE')";
    private static final String DOCUMENT_TYPE_DELETE = "hasAuthority('DOCUMENT_TYPE_DELETE')";

    private static final String MARITAL_STATUS_VIEW   = "hasAuthority('MARITAL_STATUS_VIEW_ALL')";
    private static final String MARITAL_STATUS_CREATE = "hasAuthority('MARITAL_STATUS_CREATE')";
    private static final String MARITAL_STATUS_UPDATE = "hasAuthority('MARITAL_STATUS_UPDATE')";
    private static final String MARITAL_STATUS_DELETE = "hasAuthority('MARITAL_STATUS_DELETE')";

    private static final String OCCUPATION_VIEW   = "hasAuthority('OCCUPATION_VIEW_ALL')";
    private static final String OCCUPATION_CREATE = "hasAuthority('OCCUPATION_CREATE')";
    private static final String OCCUPATION_UPDATE = "hasAuthority('OCCUPATION_UPDATE')";
    private static final String OCCUPATION_DELETE = "hasAuthority('OCCUPATION_DELETE')";

    private static final String PROFESSION_VIEW   = "hasAuthority('PROFESSION_VIEW_ALL')";
    private static final String PROFESSION_CREATE = "hasAuthority('PROFESSION_CREATE')";
    private static final String PROFESSION_UPDATE = "hasAuthority('PROFESSION_UPDATE')";
    private static final String PROFESSION_DELETE = "hasAuthority('PROFESSION_DELETE')";

    private static final String SERVICE_CATEGORY_VIEW   = "hasAuthority('SERVICE_CATEGORY_VIEW_ALL')";
    private static final String SERVICE_CATEGORY_CREATE = "hasAuthority('SERVICE_CATEGORY_CREATE')";
    private static final String SERVICE_CATEGORY_UPDATE = "hasAuthority('SERVICE_CATEGORY_UPDATE')";
    private static final String SERVICE_CATEGORY_DELETE = "hasAuthority('SERVICE_CATEGORY_DELETE')";

    private static final String ALLY_TYPE_VIEW   = "hasAuthority('ALLY_TYPE_VIEW_ALL')";
    private static final String ALLY_TYPE_CREATE = "hasAuthority('ALLY_TYPE_CREATE')";
    private static final String ALLY_TYPE_UPDATE = "hasAuthority('ALLY_TYPE_UPDATE')";
    private static final String ALLY_TYPE_DELETE = "hasAuthority('ALLY_TYPE_DELETE')";

    private static final String PROMOTER_TYPE_VIEW   = "hasAuthority('PROMOTER_TYPE_VIEW_ALL')";
    private static final String PROMOTER_TYPE_CREATE = "hasAuthority('PROMOTER_TYPE_CREATE')";
    private static final String PROMOTER_TYPE_UPDATE = "hasAuthority('PROMOTER_TYPE_UPDATE')";
    private static final String PROMOTER_TYPE_DELETE = "hasAuthority('PROMOTER_TYPE_DELETE')";

    private final CountryService countryService;
    private final StateService stateService;
    private final CityService cityService;
    private final GenderService genderService;
    private final DocumentTypeService documentTypeService;
    private final MaritalStatusService maritalStatusService;
    private final OccupationService occupationService;
    private final ProfessionService professionService;
    private final ServiceCategoryService serviceCategoryService;
    private final AllyTypeService allyTypeService;
    private final PromoterTypeService promoterTypeService;

    // ═══════════════ countries ═══════════════
    @GetMapping("/countries")
    @PreAuthorize(COUNTRY_VIEW)
    public ResponseEntity<AppliedSortPage<CountryDto>> listCountries(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<CountryDto> page = countryService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, countryService.effectiveSort(pageable)));
    }

    @GetMapping("/countries/options")
    @PreAuthorize(COUNTRY_VIEW)
    public ResponseEntity<List<OptionDto>> countryOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(countryService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/countries/{uuid}")
    @PreAuthorize(COUNTRY_VIEW)
    public ResponseEntity<CountryDto> getCountry(@PathVariable UUID uuid) {
        return ResponseEntity.ok(countryService.get(uuid));
    }

    @PostMapping("/countries")
    @PreAuthorize(COUNTRY_CREATE)
    public ResponseEntity<CountryDto> createCountry(@Valid @RequestBody CountryCreateRequest req) {
        CountryDto created = countryService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/countries/{uuid}")
    @PreAuthorize(COUNTRY_UPDATE)
    public ResponseEntity<CountryDto> updateCountry(@PathVariable UUID uuid,
                                                    @Valid @RequestBody CountryUpdateRequest req) {
        return ResponseEntity.ok(countryService.update(uuid, req));
    }

    @GetMapping("/countries/{uuid}/usage")
    @PreAuthorize(COUNTRY_VIEW)
    public ResponseEntity<UsageDto> countryUsage(@PathVariable UUID uuid) {
        long count = countryService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/countries/{uuid}")
    @PreAuthorize(COUNTRY_DELETE)
    public ResponseEntity<Void> deleteCountry(@PathVariable UUID uuid,
                                              @RequestParam(defaultValue = "false") boolean physical) {
        countryService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ states ═══════════════
    @GetMapping("/states")
    @PreAuthorize(STATE_VIEW)
    public ResponseEntity<AppliedSortPage<StateDto>> listStates(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(name = "country", required = false) String countryIsoCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<StateDto> page = stateService.list(pageable, filter, q, countryIsoCode, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, stateService.effectiveSort(pageable)));
    }

    @GetMapping("/states/options")
    @PreAuthorize(STATE_VIEW)
    public ResponseEntity<List<OptionDto>> stateOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues,
            @RequestParam(required = false) UUID countryUuid) {
        return ResponseEntity.ok(stateService.listOptions(q, limit, currentValues, countryUuid));
    }

    @GetMapping("/states/{uuid}")
    @PreAuthorize(STATE_VIEW)
    public ResponseEntity<StateDto> getState(@PathVariable UUID uuid) {
        return ResponseEntity.ok(stateService.get(uuid));
    }

    @PostMapping("/states")
    @PreAuthorize(STATE_CREATE)
    public ResponseEntity<StateDto> createState(@Valid @RequestBody StateCreateRequest req) {
        StateDto created = stateService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/states/{uuid}")
    @PreAuthorize(STATE_UPDATE)
    public ResponseEntity<StateDto> updateState(@PathVariable UUID uuid,
                                                @Valid @RequestBody StateUpdateRequest req) {
        return ResponseEntity.ok(stateService.update(uuid, req));
    }

    @GetMapping("/states/{uuid}/usage")
    @PreAuthorize(STATE_VIEW)
    public ResponseEntity<UsageDto> stateUsage(@PathVariable UUID uuid) {
        long count = stateService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/states/{uuid}")
    @PreAuthorize(STATE_DELETE)
    public ResponseEntity<Void> deleteState(@PathVariable UUID uuid,
                                            @RequestParam(defaultValue = "false") boolean physical) {
        stateService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ cities ═══════════════
    @GetMapping("/cities")
    @PreAuthorize(CITY_VIEW)
    public ResponseEntity<AppliedSortPage<CityDto>> listCities(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(name = "stateUuid", required = false) UUID stateUuid,
            @RequestParam(name = "stateCode", required = false) String stateCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<CityDto> page = cityService.list(pageable, filter, q, stateUuid, stateCode, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, cityService.effectiveSort(pageable)));
    }

    @GetMapping("/cities/options")
    @PreAuthorize(CITY_VIEW)
    public ResponseEntity<List<OptionDto>> cityOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues,
            @RequestParam(required = false) UUID stateUuid) {
        return ResponseEntity.ok(cityService.listOptions(q, limit, currentValues, stateUuid));
    }

    @GetMapping("/cities/{uuid}")
    @PreAuthorize(CITY_VIEW)
    public ResponseEntity<CityDto> getCity(@PathVariable UUID uuid) {
        return ResponseEntity.ok(cityService.get(uuid));
    }

    @PostMapping("/cities")
    @PreAuthorize(CITY_CREATE)
    public ResponseEntity<CityDto> createCity(@Valid @RequestBody CityCreateRequest req) {
        CityDto created = cityService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/cities/{uuid}")
    @PreAuthorize(CITY_UPDATE)
    public ResponseEntity<CityDto> updateCity(@PathVariable UUID uuid,
                                              @Valid @RequestBody CityUpdateRequest req) {
        return ResponseEntity.ok(cityService.update(uuid, req));
    }

    @GetMapping("/cities/{uuid}/usage")
    @PreAuthorize(CITY_VIEW)
    public ResponseEntity<UsageDto> cityUsage(@PathVariable UUID uuid) {
        long count = cityService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/cities/{uuid}")
    @PreAuthorize(CITY_DELETE)
    public ResponseEntity<Void> deleteCity(@PathVariable UUID uuid,
                                           @RequestParam(defaultValue = "false") boolean physical) {
        cityService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ genders ═══════════════
    @GetMapping("/genders")
    @PreAuthorize(GENDER_VIEW)
    public ResponseEntity<AppliedSortPage<GenderDto>> listGenders(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<GenderDto> page = genderService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, genderService.effectiveSort(pageable)));
    }

    @GetMapping("/genders/options")
    @PreAuthorize(GENDER_VIEW)
    public ResponseEntity<List<OptionDto>> genderOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(genderService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/genders/{uuid}")
    @PreAuthorize(GENDER_VIEW)
    public ResponseEntity<GenderDto> getGender(@PathVariable UUID uuid) {
        return ResponseEntity.ok(genderService.get(uuid));
    }

    @PostMapping("/genders")
    @PreAuthorize(GENDER_CREATE)
    public ResponseEntity<GenderDto> createGender(@Valid @RequestBody GenderCreateRequest req) {
        GenderDto created = genderService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/genders/{uuid}")
    @PreAuthorize(GENDER_UPDATE)
    public ResponseEntity<GenderDto> updateGender(@PathVariable UUID uuid,
                                                  @Valid @RequestBody GenderUpdateRequest req) {
        return ResponseEntity.ok(genderService.update(uuid, req));
    }

    @GetMapping("/genders/{uuid}/usage")
    @PreAuthorize(GENDER_VIEW)
    public ResponseEntity<UsageDto> genderUsage(@PathVariable UUID uuid) {
        long count = genderService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/genders/{uuid}")
    @PreAuthorize(GENDER_DELETE)
    public ResponseEntity<Void> deleteGender(@PathVariable UUID uuid,
                                             @RequestParam(defaultValue = "false") boolean physical) {
        genderService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ document-types ═══════════════
    @GetMapping("/document-types")
    @PreAuthorize(DOCUMENT_TYPE_VIEW)
    public ResponseEntity<AppliedSortPage<DocumentTypeDto>> listDocumentTypes(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<DocumentTypeDto> page = documentTypeService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, documentTypeService.effectiveSort(pageable)));
    }

    @GetMapping("/document-types/options")
    @PreAuthorize(DOCUMENT_TYPE_VIEW)
    public ResponseEntity<List<OptionDto>> documentTypeOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(documentTypeService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/document-types/{uuid}")
    @PreAuthorize(DOCUMENT_TYPE_VIEW)
    public ResponseEntity<DocumentTypeDto> getDocumentType(@PathVariable UUID uuid) {
        return ResponseEntity.ok(documentTypeService.get(uuid));
    }

    @PostMapping("/document-types")
    @PreAuthorize(DOCUMENT_TYPE_CREATE)
    public ResponseEntity<DocumentTypeDto> createDocumentType(@Valid @RequestBody DocumentTypeCreateRequest req) {
        DocumentTypeDto created = documentTypeService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/document-types/{uuid}")
    @PreAuthorize(DOCUMENT_TYPE_UPDATE)
    public ResponseEntity<DocumentTypeDto> updateDocumentType(@PathVariable UUID uuid,
                                                              @Valid @RequestBody DocumentTypeUpdateRequest req) {
        return ResponseEntity.ok(documentTypeService.update(uuid, req));
    }

    @GetMapping("/document-types/{uuid}/usage")
    @PreAuthorize(DOCUMENT_TYPE_VIEW)
    public ResponseEntity<UsageDto> documentTypeUsage(@PathVariable UUID uuid) {
        long count = documentTypeService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/document-types/{uuid}")
    @PreAuthorize(DOCUMENT_TYPE_DELETE)
    public ResponseEntity<Void> deleteDocumentType(@PathVariable UUID uuid,
                                                   @RequestParam(defaultValue = "false") boolean physical) {
        documentTypeService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ marital-statuses ═══════════════
    @GetMapping("/marital-statuses")
    @PreAuthorize(MARITAL_STATUS_VIEW)
    public ResponseEntity<AppliedSortPage<MaritalStatusDto>> listMaritalStatuses(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<MaritalStatusDto> page = maritalStatusService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, maritalStatusService.effectiveSort(pageable)));
    }

    @GetMapping("/marital-statuses/options")
    @PreAuthorize(MARITAL_STATUS_VIEW)
    public ResponseEntity<List<OptionDto>> maritalStatusOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(maritalStatusService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/marital-statuses/{uuid}")
    @PreAuthorize(MARITAL_STATUS_VIEW)
    public ResponseEntity<MaritalStatusDto> getMaritalStatus(@PathVariable UUID uuid) {
        return ResponseEntity.ok(maritalStatusService.get(uuid));
    }

    @PostMapping("/marital-statuses")
    @PreAuthorize(MARITAL_STATUS_CREATE)
    public ResponseEntity<MaritalStatusDto> createMaritalStatus(@Valid @RequestBody MaritalStatusCreateRequest req) {
        MaritalStatusDto created = maritalStatusService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/marital-statuses/{uuid}")
    @PreAuthorize(MARITAL_STATUS_UPDATE)
    public ResponseEntity<MaritalStatusDto> updateMaritalStatus(@PathVariable UUID uuid,
                                                                @Valid @RequestBody MaritalStatusUpdateRequest req) {
        return ResponseEntity.ok(maritalStatusService.update(uuid, req));
    }

    @GetMapping("/marital-statuses/{uuid}/usage")
    @PreAuthorize(MARITAL_STATUS_VIEW)
    public ResponseEntity<UsageDto> maritalStatusUsage(@PathVariable UUID uuid) {
        long count = maritalStatusService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/marital-statuses/{uuid}")
    @PreAuthorize(MARITAL_STATUS_DELETE)
    public ResponseEntity<Void> deleteMaritalStatus(@PathVariable UUID uuid,
                                                    @RequestParam(defaultValue = "false") boolean physical) {
        maritalStatusService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ occupations ═══════════════
    @GetMapping("/occupations")
    @PreAuthorize(OCCUPATION_VIEW)
    public ResponseEntity<AppliedSortPage<OccupationDto>> listOccupations(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<OccupationDto> page = occupationService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, occupationService.effectiveSort(pageable)));
    }

    @GetMapping("/occupations/options")
    @PreAuthorize(OCCUPATION_VIEW)
    public ResponseEntity<List<OptionDto>> occupationOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(occupationService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/occupations/{uuid}")
    @PreAuthorize(OCCUPATION_VIEW)
    public ResponseEntity<OccupationDto> getOccupation(@PathVariable UUID uuid) {
        return ResponseEntity.ok(occupationService.get(uuid));
    }

    @PostMapping("/occupations")
    @PreAuthorize(OCCUPATION_CREATE)
    public ResponseEntity<OccupationDto> createOccupation(@Valid @RequestBody OccupationCreateRequest req) {
        OccupationDto created = occupationService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/occupations/{uuid}")
    @PreAuthorize(OCCUPATION_UPDATE)
    public ResponseEntity<OccupationDto> updateOccupation(@PathVariable UUID uuid,
                                                          @Valid @RequestBody OccupationUpdateRequest req) {
        return ResponseEntity.ok(occupationService.update(uuid, req));
    }

    @GetMapping("/occupations/{uuid}/usage")
    @PreAuthorize(OCCUPATION_VIEW)
    public ResponseEntity<UsageDto> occupationUsage(@PathVariable UUID uuid) {
        long count = occupationService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/occupations/{uuid}")
    @PreAuthorize(OCCUPATION_DELETE)
    public ResponseEntity<Void> deleteOccupation(@PathVariable UUID uuid,
                                                 @RequestParam(defaultValue = "false") boolean physical) {
        occupationService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ professions ═══════════════
    @GetMapping("/professions")
    @PreAuthorize(PROFESSION_VIEW)
    public ResponseEntity<AppliedSortPage<ProfessionDto>> listProfessions(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<ProfessionDto> page = professionService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, professionService.effectiveSort(pageable)));
    }

    @GetMapping("/professions/options")
    @PreAuthorize(PROFESSION_VIEW)
    public ResponseEntity<List<OptionDto>> professionOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(professionService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/professions/{uuid}")
    @PreAuthorize(PROFESSION_VIEW)
    public ResponseEntity<ProfessionDto> getProfession(@PathVariable UUID uuid) {
        return ResponseEntity.ok(professionService.get(uuid));
    }

    @PostMapping("/professions")
    @PreAuthorize(PROFESSION_CREATE)
    public ResponseEntity<ProfessionDto> createProfession(@Valid @RequestBody ProfessionCreateRequest req) {
        ProfessionDto created = professionService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/professions/{uuid}")
    @PreAuthorize(PROFESSION_UPDATE)
    public ResponseEntity<ProfessionDto> updateProfession(@PathVariable UUID uuid,
                                                                      @Valid @RequestBody ProfessionUpdateRequest req) {
        return ResponseEntity.ok(professionService.update(uuid, req));
    }

    @GetMapping("/professions/{uuid}/usage")
    @PreAuthorize(PROFESSION_VIEW)
    public ResponseEntity<UsageDto> professionUsage(@PathVariable UUID uuid) {
        long count = professionService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/professions/{uuid}")
    @PreAuthorize(PROFESSION_DELETE)
    public ResponseEntity<Void> deleteProfession(@PathVariable UUID uuid,
                                                       @RequestParam(defaultValue = "false") boolean physical) {
        professionService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ service-categories ═══════════════
    @GetMapping("/service-categories")
    @PreAuthorize(SERVICE_CATEGORY_VIEW)
    public ResponseEntity<AppliedSortPage<ServiceCategoryDto>> listServiceCategories(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<ServiceCategoryDto> page = serviceCategoryService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, serviceCategoryService.effectiveSort(pageable)));
    }

    @GetMapping("/service-categories/{uuid}")
    @PreAuthorize(SERVICE_CATEGORY_VIEW)
    public ResponseEntity<ServiceCategoryDto> getServiceCategory(@PathVariable UUID uuid) {
        return ResponseEntity.ok(serviceCategoryService.get(uuid));
    }

    @PostMapping("/service-categories")
    @PreAuthorize(SERVICE_CATEGORY_CREATE)
    public ResponseEntity<ServiceCategoryDto> createServiceCategory(@Valid @RequestBody ServiceCategoryCreateRequest req) {
        ServiceCategoryDto created = serviceCategoryService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/service-categories/{uuid}")
    @PreAuthorize(SERVICE_CATEGORY_UPDATE)
    public ResponseEntity<ServiceCategoryDto> updateServiceCategory(@PathVariable UUID uuid,
                                                                    @Valid @RequestBody ServiceCategoryUpdateRequest req) {
        return ResponseEntity.ok(serviceCategoryService.update(uuid, req));
    }

    @GetMapping("/service-categories/{uuid}/usage")
    @PreAuthorize(SERVICE_CATEGORY_VIEW)
    public ResponseEntity<UsageDto> serviceCategoryUsage(@PathVariable UUID uuid) {
        long count = serviceCategoryService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/service-categories/{uuid}")
    @PreAuthorize(SERVICE_CATEGORY_DELETE)
    public ResponseEntity<Void> deleteServiceCategory(@PathVariable UUID uuid,
                                                      @RequestParam(defaultValue = "false") boolean physical) {
        serviceCategoryService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ ally-types ═══════════════
    @GetMapping("/ally-types")
    @PreAuthorize(ALLY_TYPE_VIEW)
    public ResponseEntity<AppliedSortPage<AllyTypeDto>> listAllyTypes(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<AllyTypeDto> page = allyTypeService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, allyTypeService.effectiveSort(pageable)));
    }

    @GetMapping("/ally-types/options")
    @PreAuthorize(ALLY_TYPE_VIEW)
    public ResponseEntity<List<OptionDto>> allyTypeOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(allyTypeService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/ally-types/{uuid}")
    @PreAuthorize(ALLY_TYPE_VIEW)
    public ResponseEntity<AllyTypeDto> getAllyType(@PathVariable UUID uuid) {
        return ResponseEntity.ok(allyTypeService.get(uuid));
    }

    @PostMapping("/ally-types")
    @PreAuthorize(ALLY_TYPE_CREATE)
    public ResponseEntity<AllyTypeDto> createAllyType(@Valid @RequestBody AllyTypeCreateRequest req) {
        AllyTypeDto created = allyTypeService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/ally-types/{uuid}")
    @PreAuthorize(ALLY_TYPE_UPDATE)
    public ResponseEntity<AllyTypeDto> updateAllyType(@PathVariable UUID uuid,
                                                      @Valid @RequestBody AllyTypeUpdateRequest req) {
        return ResponseEntity.ok(allyTypeService.update(uuid, req));
    }

    @GetMapping("/ally-types/{uuid}/usage")
    @PreAuthorize(ALLY_TYPE_VIEW)
    public ResponseEntity<UsageDto> allyTypeUsage(@PathVariable UUID uuid) {
        long count = allyTypeService.countUsages(uuid);
        return ResponseEntity.ok(new UsageDto(count > 0, count));
    }

    @DeleteMapping("/ally-types/{uuid}")
    @PreAuthorize(ALLY_TYPE_DELETE)
    public ResponseEntity<Void> deleteAllyType(@PathVariable UUID uuid,
                                               @RequestParam(defaultValue = "false") boolean physical) {
        allyTypeService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ promoter-types ═══════════════
    @GetMapping("/promoter-types")
    @PreAuthorize(PROMOTER_TYPE_VIEW)
    public ResponseEntity<AppliedSortPage<PromoterTypeDto>> listPromoterTypes(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<PromoterTypeDto> page = promoterTypeService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, promoterTypeService.effectiveSort(pageable)));
    }

    @GetMapping("/promoter-types/options")
    @PreAuthorize(PROMOTER_TYPE_VIEW)
    public ResponseEntity<List<OptionDto>> promoterTypeOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(promoterTypeService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/promoter-types/{uuid}")
    @PreAuthorize(PROMOTER_TYPE_VIEW)
    public ResponseEntity<PromoterTypeDto> getPromoterType(@PathVariable UUID uuid) {
        return ResponseEntity.ok(promoterTypeService.get(uuid));
    }

    @PostMapping("/promoter-types")
    @PreAuthorize(PROMOTER_TYPE_CREATE)
    public ResponseEntity<PromoterTypeDto> createPromoterType(@Valid @RequestBody PromoterTypeCreateRequest req) {
        PromoterTypeDto created = promoterTypeService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/promoter-types/{uuid}")
    @PreAuthorize(PROMOTER_TYPE_UPDATE)
    public ResponseEntity<PromoterTypeDto> updatePromoterType(@PathVariable UUID uuid,
                                                      @Valid @RequestBody PromoterTypeUpdateRequest req) {
        return ResponseEntity.ok(promoterTypeService.update(uuid, req));
    }

    @DeleteMapping("/promoter-types/{uuid}")
    @PreAuthorize(PROMOTER_TYPE_DELETE)
    public ResponseEntity<Void> deletePromoterType(@PathVariable UUID uuid) {
        promoterTypeService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    private static URI locationFor(UUID uuid) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(uuid)
                .toUri();
    }
}
