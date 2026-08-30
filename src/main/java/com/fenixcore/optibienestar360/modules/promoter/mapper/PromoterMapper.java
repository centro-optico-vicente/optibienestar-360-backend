package com.fenixcore.optibienestar360.modules.promoter.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.mapstruct.Mapper;

/**
 * {@code user} / {@code person} / {@code promoterType} map to
 * {@link com.fenixcore.optibienestar360.core.display.DisplayRef} via
 * {@link DisplayRefs}; the serializer flattens them to
 * {@code <rel>_Uuid} + {@code <rel>_Display} (ADR 0014).
 */
@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface PromoterMapper {

    PromoterDto toDto(Promoter promoter);
}
