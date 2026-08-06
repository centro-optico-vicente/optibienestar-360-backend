package com.fenixcore.optibienestar360.modules.catalog;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.MedicalSpecialtyCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.MedicalSpecialtyDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.MedicalSpecialtyUpdateRequest;
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
import com.fenixcore.optibienestar360.modules.catalog.dto.StateCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.StateDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.StateUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.service.AllyTypeService;
import com.fenixcore.optibienestar360.modules.catalog.service.CityService;
import com.fenixcore.optibienestar360.modules.catalog.service.CountryService;
import com.fenixcore.optibienestar360.modules.catalog.service.DocumentTypeService;
import com.fenixcore.optibienestar360.modules.catalog.service.GenderService;
import com.fenixcore.optibienestar360.modules.catalog.service.MaritalStatusService;
import com.fenixcore.optibienestar360.modules.catalog.service.MedicalSpecialtyService;
import com.fenixcore.optibienestar360.modules.catalog.service.OccupationService;
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
 * <p>Authorization:
 * <ul>
 *   <li><b>READ</b> requires {@code CATALOG_VIEW_ALL} (V34) — one coarse read key
 *       for all catalogs. Reads stay coarse on purpose: forms across the app
 *       populate their dropdowns from these same endpoints, so a per-catalog read
 *       key would break unrelated screens. V34 grants this to every role that
 *       previously read catalogs via {@code USER_VIEW_ALL} plus every catalog
 *       writer, so the switch is behavior-preserving and no longer borrows a
 *       USERS-domain permission.</li>
 *   <li><b>WRITE</b> requires that catalog's own {@code CATALOG_*_WRITE} key
 *       (V33). One key per catalog so access can be delegated per catalog — an
 *       HR-style role can be granted occupations without also getting countries.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/admin/catalogs")
@RequiredArgsConstructor
public class AdminCatalogsController {

    private static final String READ_AUTH = "hasAuthority('CATALOG_VIEW_ALL')";

    private static final String COUNTRY_WRITE            = "hasAuthority('CATALOG_COUNTRY_WRITE')";
    private static final String STATE_WRITE              = "hasAuthority('CATALOG_STATE_WRITE')";
    private static final String CITY_WRITE               = "hasAuthority('CATALOG_CITY_WRITE')";
    private static final String GENDER_WRITE             = "hasAuthority('CATALOG_GENDER_WRITE')";
    private static final String DOCUMENT_TYPE_WRITE      = "hasAuthority('CATALOG_DOCUMENT_TYPE_WRITE')";
    private static final String MARITAL_STATUS_WRITE     = "hasAuthority('CATALOG_MARITAL_STATUS_WRITE')";
    private static final String OCCUPATION_WRITE         = "hasAuthority('CATALOG_OCCUPATION_WRITE')";
    private static final String MEDICAL_SPECIALTY_WRITE  = "hasAuthority('CATALOG_MEDICAL_SPECIALTY_WRITE')";
    private static final String SERVICE_CATEGORY_WRITE   = "hasAuthority('CATALOG_SERVICE_CATEGORY_WRITE')";
    private static final String ALLY_TYPE_WRITE          = "hasAuthority('CATALOG_ALLY_TYPE_WRITE')";

    private final CountryService countryService;
    private final StateService stateService;
    private final CityService cityService;
    private final GenderService genderService;
    private final DocumentTypeService documentTypeService;
    private final MaritalStatusService maritalStatusService;
    private final OccupationService occupationService;
    private final MedicalSpecialtyService medicalSpecialtyService;
    private final ServiceCategoryService serviceCategoryService;
    private final AllyTypeService allyTypeService;

    // ═══════════════ countries ═══════════════
    @GetMapping("/countries")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<CountryDto>> listCountries(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(countryService.list(pageable, filter, q));
    }

    @GetMapping("/countries/options")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OptionDto>> countryOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(countryService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/countries/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<CountryDto> getCountry(@PathVariable UUID uuid) {
        return ResponseEntity.ok(countryService.get(uuid));
    }

    @PostMapping("/countries")
    @PreAuthorize(COUNTRY_WRITE)
    public ResponseEntity<CountryDto> createCountry(@Valid @RequestBody CountryCreateRequest req) {
        CountryDto created = countryService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/countries/{uuid}")
    @PreAuthorize(COUNTRY_WRITE)
    public ResponseEntity<CountryDto> updateCountry(@PathVariable UUID uuid,
                                                    @Valid @RequestBody CountryUpdateRequest req) {
        return ResponseEntity.ok(countryService.update(uuid, req));
    }

    @DeleteMapping("/countries/{uuid}")
    @PreAuthorize(COUNTRY_WRITE)
    public ResponseEntity<Void> deleteCountry(@PathVariable UUID uuid) {
        countryService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ states ═══════════════
    @GetMapping("/states")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<StateDto>> listStates(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(name = "country", required = false) String countryIsoCode) {
        return ResponseEntity.ok(stateService.list(pageable, filter, q, countryIsoCode));
    }

    @GetMapping("/states/options")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OptionDto>> stateOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues,
            @RequestParam(required = false) UUID countryUuid) {
        return ResponseEntity.ok(stateService.listOptions(q, limit, currentValues, countryUuid));
    }

    @GetMapping("/states/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<StateDto> getState(@PathVariable UUID uuid) {
        return ResponseEntity.ok(stateService.get(uuid));
    }

    @PostMapping("/states")
    @PreAuthorize(STATE_WRITE)
    public ResponseEntity<StateDto> createState(@Valid @RequestBody StateCreateRequest req) {
        StateDto created = stateService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/states/{uuid}")
    @PreAuthorize(STATE_WRITE)
    public ResponseEntity<StateDto> updateState(@PathVariable UUID uuid,
                                                @Valid @RequestBody StateUpdateRequest req) {
        return ResponseEntity.ok(stateService.update(uuid, req));
    }

    @DeleteMapping("/states/{uuid}")
    @PreAuthorize(STATE_WRITE)
    public ResponseEntity<Void> deleteState(@PathVariable UUID uuid) {
        stateService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ cities ═══════════════
    @GetMapping("/cities")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<CityDto>> listCities(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(name = "stateUuid", required = false) UUID stateUuid,
            @RequestParam(name = "stateCode", required = false) String stateCode) {
        return ResponseEntity.ok(cityService.list(pageable, filter, q, stateUuid, stateCode));
    }

    @GetMapping("/cities/options")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OptionDto>> cityOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues,
            @RequestParam(required = false) UUID stateUuid) {
        return ResponseEntity.ok(cityService.listOptions(q, limit, currentValues, stateUuid));
    }

    @GetMapping("/cities/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<CityDto> getCity(@PathVariable UUID uuid) {
        return ResponseEntity.ok(cityService.get(uuid));
    }

    @PostMapping("/cities")
    @PreAuthorize(CITY_WRITE)
    public ResponseEntity<CityDto> createCity(@Valid @RequestBody CityCreateRequest req) {
        CityDto created = cityService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/cities/{uuid}")
    @PreAuthorize(CITY_WRITE)
    public ResponseEntity<CityDto> updateCity(@PathVariable UUID uuid,
                                              @Valid @RequestBody CityUpdateRequest req) {
        return ResponseEntity.ok(cityService.update(uuid, req));
    }

    @DeleteMapping("/cities/{uuid}")
    @PreAuthorize(CITY_WRITE)
    public ResponseEntity<Void> deleteCity(@PathVariable UUID uuid) {
        cityService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ genders ═══════════════
    @GetMapping("/genders")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<GenderDto>> listGenders(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(genderService.list(pageable, filter, q));
    }

    @GetMapping("/genders/options")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OptionDto>> genderOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(genderService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/genders/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<GenderDto> getGender(@PathVariable UUID uuid) {
        return ResponseEntity.ok(genderService.get(uuid));
    }

    @PostMapping("/genders")
    @PreAuthorize(GENDER_WRITE)
    public ResponseEntity<GenderDto> createGender(@Valid @RequestBody GenderCreateRequest req) {
        GenderDto created = genderService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/genders/{uuid}")
    @PreAuthorize(GENDER_WRITE)
    public ResponseEntity<GenderDto> updateGender(@PathVariable UUID uuid,
                                                  @Valid @RequestBody GenderUpdateRequest req) {
        return ResponseEntity.ok(genderService.update(uuid, req));
    }

    @DeleteMapping("/genders/{uuid}")
    @PreAuthorize(GENDER_WRITE)
    public ResponseEntity<Void> deleteGender(@PathVariable UUID uuid) {
        genderService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ document-types ═══════════════
    @GetMapping("/document-types")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<DocumentTypeDto>> listDocumentTypes(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(documentTypeService.list(pageable, filter, q));
    }

    @GetMapping("/document-types/options")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OptionDto>> documentTypeOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(documentTypeService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/document-types/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<DocumentTypeDto> getDocumentType(@PathVariable UUID uuid) {
        return ResponseEntity.ok(documentTypeService.get(uuid));
    }

    @PostMapping("/document-types")
    @PreAuthorize(DOCUMENT_TYPE_WRITE)
    public ResponseEntity<DocumentTypeDto> createDocumentType(@Valid @RequestBody DocumentTypeCreateRequest req) {
        DocumentTypeDto created = documentTypeService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/document-types/{uuid}")
    @PreAuthorize(DOCUMENT_TYPE_WRITE)
    public ResponseEntity<DocumentTypeDto> updateDocumentType(@PathVariable UUID uuid,
                                                              @Valid @RequestBody DocumentTypeUpdateRequest req) {
        return ResponseEntity.ok(documentTypeService.update(uuid, req));
    }

    @DeleteMapping("/document-types/{uuid}")
    @PreAuthorize(DOCUMENT_TYPE_WRITE)
    public ResponseEntity<Void> deleteDocumentType(@PathVariable UUID uuid) {
        documentTypeService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ marital-statuses ═══════════════
    @GetMapping("/marital-statuses")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<MaritalStatusDto>> listMaritalStatuses(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(maritalStatusService.list(pageable, filter, q));
    }

    @GetMapping("/marital-statuses/options")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OptionDto>> maritalStatusOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(maritalStatusService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/marital-statuses/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<MaritalStatusDto> getMaritalStatus(@PathVariable UUID uuid) {
        return ResponseEntity.ok(maritalStatusService.get(uuid));
    }

    @PostMapping("/marital-statuses")
    @PreAuthorize(MARITAL_STATUS_WRITE)
    public ResponseEntity<MaritalStatusDto> createMaritalStatus(@Valid @RequestBody MaritalStatusCreateRequest req) {
        MaritalStatusDto created = maritalStatusService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/marital-statuses/{uuid}")
    @PreAuthorize(MARITAL_STATUS_WRITE)
    public ResponseEntity<MaritalStatusDto> updateMaritalStatus(@PathVariable UUID uuid,
                                                                @Valid @RequestBody MaritalStatusUpdateRequest req) {
        return ResponseEntity.ok(maritalStatusService.update(uuid, req));
    }

    @DeleteMapping("/marital-statuses/{uuid}")
    @PreAuthorize(MARITAL_STATUS_WRITE)
    public ResponseEntity<Void> deleteMaritalStatus(@PathVariable UUID uuid) {
        maritalStatusService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ occupations ═══════════════
    @GetMapping("/occupations")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<OccupationDto>> listOccupations(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(occupationService.list(pageable, filter, q));
    }

    @GetMapping("/occupations/options")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OptionDto>> occupationOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(occupationService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/occupations/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<OccupationDto> getOccupation(@PathVariable UUID uuid) {
        return ResponseEntity.ok(occupationService.get(uuid));
    }

    @PostMapping("/occupations")
    @PreAuthorize(OCCUPATION_WRITE)
    public ResponseEntity<OccupationDto> createOccupation(@Valid @RequestBody OccupationCreateRequest req) {
        OccupationDto created = occupationService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/occupations/{uuid}")
    @PreAuthorize(OCCUPATION_WRITE)
    public ResponseEntity<OccupationDto> updateOccupation(@PathVariable UUID uuid,
                                                          @Valid @RequestBody OccupationUpdateRequest req) {
        return ResponseEntity.ok(occupationService.update(uuid, req));
    }

    @DeleteMapping("/occupations/{uuid}")
    @PreAuthorize(OCCUPATION_WRITE)
    public ResponseEntity<Void> deleteOccupation(@PathVariable UUID uuid) {
        occupationService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ medical-specialties ═══════════════
    @GetMapping("/medical-specialties")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<MedicalSpecialtyDto>> listMedicalSpecialties(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(medicalSpecialtyService.list(pageable, filter, q));
    }

    @GetMapping("/medical-specialties/options")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OptionDto>> medicalSpecialtyOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(medicalSpecialtyService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/medical-specialties/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<MedicalSpecialtyDto> getMedicalSpecialty(@PathVariable UUID uuid) {
        return ResponseEntity.ok(medicalSpecialtyService.get(uuid));
    }

    @PostMapping("/medical-specialties")
    @PreAuthorize(MEDICAL_SPECIALTY_WRITE)
    public ResponseEntity<MedicalSpecialtyDto> createMedicalSpecialty(@Valid @RequestBody MedicalSpecialtyCreateRequest req) {
        MedicalSpecialtyDto created = medicalSpecialtyService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/medical-specialties/{uuid}")
    @PreAuthorize(MEDICAL_SPECIALTY_WRITE)
    public ResponseEntity<MedicalSpecialtyDto> updateMedicalSpecialty(@PathVariable UUID uuid,
                                                                      @Valid @RequestBody MedicalSpecialtyUpdateRequest req) {
        return ResponseEntity.ok(medicalSpecialtyService.update(uuid, req));
    }

    @DeleteMapping("/medical-specialties/{uuid}")
    @PreAuthorize(MEDICAL_SPECIALTY_WRITE)
    public ResponseEntity<Void> deleteMedicalSpecialty(@PathVariable UUID uuid) {
        medicalSpecialtyService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ service-categories ═══════════════
    @GetMapping("/service-categories")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<ServiceCategoryDto>> listServiceCategories(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(serviceCategoryService.list(pageable, filter, q));
    }

    @GetMapping("/service-categories/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<ServiceCategoryDto> getServiceCategory(@PathVariable UUID uuid) {
        return ResponseEntity.ok(serviceCategoryService.get(uuid));
    }

    @PostMapping("/service-categories")
    @PreAuthorize(SERVICE_CATEGORY_WRITE)
    public ResponseEntity<ServiceCategoryDto> createServiceCategory(@Valid @RequestBody ServiceCategoryCreateRequest req) {
        ServiceCategoryDto created = serviceCategoryService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/service-categories/{uuid}")
    @PreAuthorize(SERVICE_CATEGORY_WRITE)
    public ResponseEntity<ServiceCategoryDto> updateServiceCategory(@PathVariable UUID uuid,
                                                                    @Valid @RequestBody ServiceCategoryUpdateRequest req) {
        return ResponseEntity.ok(serviceCategoryService.update(uuid, req));
    }

    @DeleteMapping("/service-categories/{uuid}")
    @PreAuthorize(SERVICE_CATEGORY_WRITE)
    public ResponseEntity<Void> deleteServiceCategory(@PathVariable UUID uuid) {
        serviceCategoryService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ ally-types ═══════════════
    @GetMapping("/ally-types")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<AllyTypeDto>> listAllyTypes(
            @PageableDefault(size = 50, sort = "name") Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(allyTypeService.list(pageable, filter, q));
    }

    @GetMapping("/ally-types/options")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OptionDto>> allyTypeOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(allyTypeService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/ally-types/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<AllyTypeDto> getAllyType(@PathVariable UUID uuid) {
        return ResponseEntity.ok(allyTypeService.get(uuid));
    }

    @PostMapping("/ally-types")
    @PreAuthorize(ALLY_TYPE_WRITE)
    public ResponseEntity<AllyTypeDto> createAllyType(@Valid @RequestBody AllyTypeCreateRequest req) {
        AllyTypeDto created = allyTypeService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/ally-types/{uuid}")
    @PreAuthorize(ALLY_TYPE_WRITE)
    public ResponseEntity<AllyTypeDto> updateAllyType(@PathVariable UUID uuid,
                                                      @Valid @RequestBody AllyTypeUpdateRequest req) {
        return ResponseEntity.ok(allyTypeService.update(uuid, req));
    }

    @DeleteMapping("/ally-types/{uuid}")
    @PreAuthorize(ALLY_TYPE_WRITE)
    public ResponseEntity<Void> deleteAllyType(@PathVariable UUID uuid) {
        allyTypeService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    private static URI locationFor(UUID uuid) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(uuid)
                .toUri();
    }
}
