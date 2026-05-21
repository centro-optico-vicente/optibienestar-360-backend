package com.fenixcore.optisaludplus.modules.contact;

import com.fenixcore.optisaludplus.modules.contact.dto.ContactRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/public")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @PostMapping("/contact")
    @ResponseStatus(HttpStatus.CREATED)
    public void submit(@Valid @RequestBody ContactRequestDto dto) {
        contactService.submit(dto);
    }
}
