package com.fenixcore.optisaludplus.modules.catalog;

import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.CityDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.CountryDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.GenderDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MaritalStatusDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.OccupationDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.ServiceCategoryDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.StateDto;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only access to the catalog tables, exposed without authentication
 * (path is whitelisted in SecurityConfig.PUBLIC_PATHS as /v1/public/**).
 * Drives selects/dropdowns in the public landing and registration flows.
 */
@RestController
@RequestMapping("/v1/public/catalogs")
@RequiredArgsConstructor
public class PublicCatalogsController {

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

    // ─── countries ──────────────────────────────────────────────────────────
    @GetMapping("/countries")
    public ResponseEntity<List<CountryDto>> listCountries() {
        return ResponseEntity.ok(countryService.list());
    }

    @GetMapping("/countries/{uuid}")
    public ResponseEntity<CountryDto> getCountry(@PathVariable UUID uuid) {
        return ResponseEntity.ok(countryService.get(uuid));
    }

    // ─── states ─────────────────────────────────────────────────────────────
    @GetMapping("/states")
    public ResponseEntity<List<StateDto>> listStates(
            @RequestParam(name = "country", required = false) String countryIsoCode) {
        return ResponseEntity.ok(stateService.list(countryIsoCode));
    }

    @GetMapping("/states/{uuid}")
    public ResponseEntity<StateDto> getState(@PathVariable UUID uuid) {
        return ResponseEntity.ok(stateService.get(uuid));
    }

    // ─── cities ─────────────────────────────────────────────────────────────
    @GetMapping("/cities")
    public ResponseEntity<List<CityDto>> listCities(
            @RequestParam(name = "stateUuid", required = false) UUID stateUuid,
            @RequestParam(name = "stateCode", required = false) String stateCode) {
        return ResponseEntity.ok(cityService.list(stateUuid, stateCode));
    }

    @GetMapping("/cities/{uuid}")
    public ResponseEntity<CityDto> getCity(@PathVariable UUID uuid) {
        return ResponseEntity.ok(cityService.get(uuid));
    }

    // ─── genders ────────────────────────────────────────────────────────────
    @GetMapping("/genders")
    public ResponseEntity<List<GenderDto>> listGenders() {
        return ResponseEntity.ok(genderService.list());
    }

    @GetMapping("/genders/{uuid}")
    public ResponseEntity<GenderDto> getGender(@PathVariable UUID uuid) {
        return ResponseEntity.ok(genderService.get(uuid));
    }

    // ─── document-types ─────────────────────────────────────────────────────
    @GetMapping("/document-types")
    public ResponseEntity<List<DocumentTypeDto>> listDocumentTypes() {
        return ResponseEntity.ok(documentTypeService.list());
    }

    @GetMapping("/document-types/{uuid}")
    public ResponseEntity<DocumentTypeDto> getDocumentType(@PathVariable UUID uuid) {
        return ResponseEntity.ok(documentTypeService.get(uuid));
    }

    // ─── marital-statuses ───────────────────────────────────────────────────
    @GetMapping("/marital-statuses")
    public ResponseEntity<List<MaritalStatusDto>> listMaritalStatuses() {
        return ResponseEntity.ok(maritalStatusService.list());
    }

    @GetMapping("/marital-statuses/{uuid}")
    public ResponseEntity<MaritalStatusDto> getMaritalStatus(@PathVariable UUID uuid) {
        return ResponseEntity.ok(maritalStatusService.get(uuid));
    }

    // ─── occupations ────────────────────────────────────────────────────────
    @GetMapping("/occupations")
    public ResponseEntity<List<OccupationDto>> listOccupations() {
        return ResponseEntity.ok(occupationService.list());
    }

    @GetMapping("/occupations/{uuid}")
    public ResponseEntity<OccupationDto> getOccupation(@PathVariable UUID uuid) {
        return ResponseEntity.ok(occupationService.get(uuid));
    }

    // ─── medical-specialties ────────────────────────────────────────────────
    @GetMapping("/medical-specialties")
    public ResponseEntity<List<MedicalSpecialtyDto>> listMedicalSpecialties() {
        return ResponseEntity.ok(medicalSpecialtyService.list());
    }

    @GetMapping("/medical-specialties/{uuid}")
    public ResponseEntity<MedicalSpecialtyDto> getMedicalSpecialty(@PathVariable UUID uuid) {
        return ResponseEntity.ok(medicalSpecialtyService.get(uuid));
    }

    // ─── service-categories ─────────────────────────────────────────────────
    @GetMapping("/service-categories")
    public ResponseEntity<List<ServiceCategoryDto>> listServiceCategories() {
        return ResponseEntity.ok(serviceCategoryService.list());
    }

    @GetMapping("/service-categories/{uuid}")
    public ResponseEntity<ServiceCategoryDto> getServiceCategory(@PathVariable UUID uuid) {
        return ResponseEntity.ok(serviceCategoryService.get(uuid));
    }

    // ─── ally-types ─────────────────────────────────────────────────────────
    @GetMapping("/ally-types")
    public ResponseEntity<List<AllyTypeDto>> listAllyTypes() {
        return ResponseEntity.ok(allyTypeService.list());
    }

    @GetMapping("/ally-types/{uuid}")
    public ResponseEntity<AllyTypeDto> getAllyType(@PathVariable UUID uuid) {
        return ResponseEntity.ok(allyTypeService.get(uuid));
    }
}
