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

    /** The product's primary image URL ({@code logo_url}, hub ADR 0027); {@code null} without images. */
    static String imageUrl(Product product, String publicBaseUrl) {
        return product == null ? null : product.getLogoUrl();
    }
}
