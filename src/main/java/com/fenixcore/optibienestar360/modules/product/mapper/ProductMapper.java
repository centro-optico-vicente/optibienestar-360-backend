package com.fenixcore.optibienestar360.modules.product.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.product.dto.ProductDto;
import com.fenixcore.optibienestar360.modules.product.dto.ProductListItemDto;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface ProductMapper {

    @Mapping(target = "exclusive", expression = "java(product.isExclusive())")
    @Mapping(target = "imageUrl",  expression = "java(ProductMapper.imageUrl(product, publicBaseUrl))")
    ProductListItemDto toListItem(Product product, @Context String publicBaseUrl);

    @Mapping(target = "exclusive", expression = "java(product.isExclusive())")
    @Mapping(target = "imageUrl",  expression = "java(ProductMapper.imageUrl(product, publicBaseUrl))")
    ProductDto toDto(Product product, @Context String publicBaseUrl);

    /**
     * Derives the public catalog image URL from the persisted key —
     * {@code publicBaseUrl + imageKey} — so a change of public domain never
     * requires a data migration (spec §5). {@code null} until published.
     */
    static String imageUrl(Product product, String publicBaseUrl) {
        String imageKey = product == null ? null : product.getImageKey();
        if (imageKey == null || publicBaseUrl == null || publicBaseUrl.isBlank()) return null;
        return publicBaseUrl.endsWith("/") ? publicBaseUrl + imageKey : publicBaseUrl + "/" + imageKey;
    }
}
