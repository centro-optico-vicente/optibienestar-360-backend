package com.fenixcore.optisaludplus.modules.catalog;

import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.CityCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.CityDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.CityUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.ServiceCategoryCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.ServiceCategoryDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.ServiceCategoryUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.CountryCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.CountryDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.CountryUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.GenderCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.GenderDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.GenderUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.MaritalStatusCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.MaritalStatusDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MaritalStatusUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.OccupationCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.OccupationDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.OccupationUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.StateCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.StateDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.StateUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.service.AllyTypeService;
import com.fenixcore.optisaludplus.modules.catalog.service.CityService;
import com.fenixcore.optisaludplus.modules.catalog.service.CountryService;
import com.fenixcore.optisaludplus.modules.catalog.service.DocumentTypeService;
import com.fenixcore.optisaludplus.modules.catalog.service.GenderService;
import com.fenixcore.optisaludplus.modules.catalog.service.MaritalStatusService;
import com.fenixcore.optisaludplus.modules.catalog.service.MedicalSpecialtyService;
import com.fenixcore.optisaludplus.modules.catalog.service.OccupationService;
import com.fenixcore.optisaludplus.modules.catalog.service.ServiceCategoryService;
import com.fenixcore.optisaludplus.modules.catalog.service.StateService;
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
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD over catalog tables.
 *
 * <p>Authorization pragmatic mapping (no dedicated CATALOG_* permissions exist
 * in V6 seed yet — see follow-up in vertical-2 checklist):
 * <ul>
 *   <li><b>READ</b> requires {@code USER_VIEW_ALL} — anyone in the admin area
 *       can list/get catalogs (SYSTEM, ADMINISTRADOR, OPERADOR variants).</li>
 *   <li><b>WRITE</b> requires {@code USER_CHANGE_ROLE} — only SYSTEM can
 *       create/update/delete; catalogs are sensitive reference data.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/admin/catalogs")
@RequiredArgsConstructor
public class AdminCatalogsController {

    private static final String READ_AUTH = "hasAuthority('USER_VIEW_ALL')";
    private static final String WRITE_AUTH = "hasAuthority('USER_CHANGE_ROLE')";

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
    public ResponseEntity<List<CountryDto>> listCountries() {
        return ResponseEntity.ok(countryService.list());
    }

    @GetMapping("/countries/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<CountryDto> getCountry(@PathVariable UUID uuid) {
        return ResponseEntity.ok(countryService.get(uuid));
    }

    @PostMapping("/countries")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<CountryDto> createCountry(@Valid @RequestBody CountryCreateRequest req) {
        CountryDto created = countryService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/countries/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<CountryDto> updateCountry(@PathVariable UUID uuid,
                                                    @Valid @RequestBody CountryUpdateRequest req) {
        return ResponseEntity.ok(countryService.update(uuid, req));
    }

    @DeleteMapping("/countries/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<Void> deleteCountry(@PathVariable UUID uuid) {
        countryService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ states ═══════════════
    @GetMapping("/states")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<StateDto>> listStates(
            @RequestParam(name = "country", required = false) String countryIsoCode) {
        return ResponseEntity.ok(stateService.list(countryIsoCode));
    }

    @GetMapping("/states/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<StateDto> getState(@PathVariable UUID uuid) {
        return ResponseEntity.ok(stateService.get(uuid));
    }

    @PostMapping("/states")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<StateDto> createState(@Valid @RequestBody StateCreateRequest req) {
        StateDto created = stateService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/states/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<StateDto> updateState(@PathVariable UUID uuid,
                                                @Valid @RequestBody StateUpdateRequest req) {
        return ResponseEntity.ok(stateService.update(uuid, req));
    }

    @DeleteMapping("/states/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<Void> deleteState(@PathVariable UUID uuid) {
        stateService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ cities ═══════════════
    @GetMapping("/cities")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<CityDto>> listCities(
            @RequestParam(name = "stateUuid", required = false) UUID stateUuid,
            @RequestParam(name = "stateCode", required = false) String stateCode) {
        return ResponseEntity.ok(cityService.list(stateUuid, stateCode));
    }

    @GetMapping("/cities/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<CityDto> getCity(@PathVariable UUID uuid) {
        return ResponseEntity.ok(cityService.get(uuid));
    }

    @PostMapping("/cities")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<CityDto> createCity(@Valid @RequestBody CityCreateRequest req) {
        CityDto created = cityService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/cities/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<CityDto> updateCity(@PathVariable UUID uuid,
                                              @Valid @RequestBody CityUpdateRequest req) {
        return ResponseEntity.ok(cityService.update(uuid, req));
    }

    @DeleteMapping("/cities/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<Void> deleteCity(@PathVariable UUID uuid) {
        cityService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ genders ═══════════════
    @GetMapping("/genders")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<GenderDto>> listGenders() {
        return ResponseEntity.ok(genderService.list());
    }

    @GetMapping("/genders/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<GenderDto> getGender(@PathVariable UUID uuid) {
        return ResponseEntity.ok(genderService.get(uuid));
    }

    @PostMapping("/genders")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<GenderDto> createGender(@Valid @RequestBody GenderCreateRequest req) {
        GenderDto created = genderService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/genders/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<GenderDto> updateGender(@PathVariable UUID uuid,
                                                  @Valid @RequestBody GenderUpdateRequest req) {
        return ResponseEntity.ok(genderService.update(uuid, req));
    }

    @DeleteMapping("/genders/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<Void> deleteGender(@PathVariable UUID uuid) {
        genderService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ document-types ═══════════════
    @GetMapping("/document-types")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<DocumentTypeDto>> listDocumentTypes() {
        return ResponseEntity.ok(documentTypeService.list());
    }

    @GetMapping("/document-types/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<DocumentTypeDto> getDocumentType(@PathVariable UUID uuid) {
        return ResponseEntity.ok(documentTypeService.get(uuid));
    }

    @PostMapping("/document-types")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<DocumentTypeDto> createDocumentType(@Valid @RequestBody DocumentTypeCreateRequest req) {
        DocumentTypeDto created = documentTypeService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/document-types/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<DocumentTypeDto> updateDocumentType(@PathVariable UUID uuid,
                                                              @Valid @RequestBody DocumentTypeUpdateRequest req) {
        return ResponseEntity.ok(documentTypeService.update(uuid, req));
    }

    @DeleteMapping("/document-types/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<Void> deleteDocumentType(@PathVariable UUID uuid) {
        documentTypeService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ marital-statuses ═══════════════
    @GetMapping("/marital-statuses")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<MaritalStatusDto>> listMaritalStatuses() {
        return ResponseEntity.ok(maritalStatusService.list());
    }

    @GetMapping("/marital-statuses/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<MaritalStatusDto> getMaritalStatus(@PathVariable UUID uuid) {
        return ResponseEntity.ok(maritalStatusService.get(uuid));
    }

    @PostMapping("/marital-statuses")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<MaritalStatusDto> createMaritalStatus(@Valid @RequestBody MaritalStatusCreateRequest req) {
        MaritalStatusDto created = maritalStatusService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/marital-statuses/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<MaritalStatusDto> updateMaritalStatus(@PathVariable UUID uuid,
                                                                @Valid @RequestBody MaritalStatusUpdateRequest req) {
        return ResponseEntity.ok(maritalStatusService.update(uuid, req));
    }

    @DeleteMapping("/marital-statuses/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<Void> deleteMaritalStatus(@PathVariable UUID uuid) {
        maritalStatusService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ occupations ═══════════════
    @GetMapping("/occupations")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<OccupationDto>> listOccupations() {
        return ResponseEntity.ok(occupationService.list());
    }

    @GetMapping("/occupations/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<OccupationDto> getOccupation(@PathVariable UUID uuid) {
        return ResponseEntity.ok(occupationService.get(uuid));
    }

    @PostMapping("/occupations")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<OccupationDto> createOccupation(@Valid @RequestBody OccupationCreateRequest req) {
        OccupationDto created = occupationService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/occupations/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<OccupationDto> updateOccupation(@PathVariable UUID uuid,
                                                          @Valid @RequestBody OccupationUpdateRequest req) {
        return ResponseEntity.ok(occupationService.update(uuid, req));
    }

    @DeleteMapping("/occupations/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<Void> deleteOccupation(@PathVariable UUID uuid) {
        occupationService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ medical-specialties ═══════════════
    @GetMapping("/medical-specialties")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<MedicalSpecialtyDto>> listMedicalSpecialties() {
        return ResponseEntity.ok(medicalSpecialtyService.list());
    }

    @GetMapping("/medical-specialties/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<MedicalSpecialtyDto> getMedicalSpecialty(@PathVariable UUID uuid) {
        return ResponseEntity.ok(medicalSpecialtyService.get(uuid));
    }

    @PostMapping("/medical-specialties")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<MedicalSpecialtyDto> createMedicalSpecialty(@Valid @RequestBody MedicalSpecialtyCreateRequest req) {
        MedicalSpecialtyDto created = medicalSpecialtyService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/medical-specialties/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<MedicalSpecialtyDto> updateMedicalSpecialty(@PathVariable UUID uuid,
                                                                      @Valid @RequestBody MedicalSpecialtyUpdateRequest req) {
        return ResponseEntity.ok(medicalSpecialtyService.update(uuid, req));
    }

    @DeleteMapping("/medical-specialties/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<Void> deleteMedicalSpecialty(@PathVariable UUID uuid) {
        medicalSpecialtyService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ service-categories ═══════════════
    @GetMapping("/service-categories")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<ServiceCategoryDto>> listServiceCategories() {
        return ResponseEntity.ok(serviceCategoryService.list());
    }

    @GetMapping("/service-categories/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<ServiceCategoryDto> getServiceCategory(@PathVariable UUID uuid) {
        return ResponseEntity.ok(serviceCategoryService.get(uuid));
    }

    @PostMapping("/service-categories")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<ServiceCategoryDto> createServiceCategory(@Valid @RequestBody ServiceCategoryCreateRequest req) {
        ServiceCategoryDto created = serviceCategoryService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/service-categories/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<ServiceCategoryDto> updateServiceCategory(@PathVariable UUID uuid,
                                                                    @Valid @RequestBody ServiceCategoryUpdateRequest req) {
        return ResponseEntity.ok(serviceCategoryService.update(uuid, req));
    }

    @DeleteMapping("/service-categories/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<Void> deleteServiceCategory(@PathVariable UUID uuid) {
        serviceCategoryService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════ ally-types ═══════════════
    @GetMapping("/ally-types")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<AllyTypeDto>> listAllyTypes() {
        return ResponseEntity.ok(allyTypeService.list());
    }

    @GetMapping("/ally-types/{uuid}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<AllyTypeDto> getAllyType(@PathVariable UUID uuid) {
        return ResponseEntity.ok(allyTypeService.get(uuid));
    }

    @PostMapping("/ally-types")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<AllyTypeDto> createAllyType(@Valid @RequestBody AllyTypeCreateRequest req) {
        AllyTypeDto created = allyTypeService.create(req);
        return ResponseEntity.created(locationFor(created.uuid())).body(created);
    }

    @PutMapping("/ally-types/{uuid}")
    @PreAuthorize(WRITE_AUTH)
    public ResponseEntity<AllyTypeDto> updateAllyType(@PathVariable UUID uuid,
                                                      @Valid @RequestBody AllyTypeUpdateRequest req) {
        return ResponseEntity.ok(allyTypeService.update(uuid, req));
    }

    @DeleteMapping("/ally-types/{uuid}")
    @PreAuthorize(WRITE_AUTH)
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
