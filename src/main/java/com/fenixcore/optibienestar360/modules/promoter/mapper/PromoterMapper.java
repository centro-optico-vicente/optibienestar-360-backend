package com.fenixcore.optibienestar360.modules.promoter.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterIdentityDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@code user} / {@code person} / {@code promoterType} map to
 * {@link com.fenixcore.optibienestar360.core.display.DisplayRef} via
 * {@link DisplayRefs}; the serializer flattens them to
 * {@code <rel>_Uuid} + {@code <rel>_Display} (ADR 0014).
 *
 * <p>{@link #toDto} is the list/compact shape ({@code identity = null});
 * {@link #toDetailDto} adds the linked person's identity block.</p>
 */
@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface PromoterMapper {

    @Mapping(target = "identity", ignore = true)
    PromoterDto toDto(Promoter promoter);

    @Mapping(target = "identity", source = "person")
    PromoterDto toDetailDto(Promoter promoter);

    @Mapping(target = "personUuid", source = "uuid")
    PromoterIdentityDto toIdentityDto(Person person);
}
