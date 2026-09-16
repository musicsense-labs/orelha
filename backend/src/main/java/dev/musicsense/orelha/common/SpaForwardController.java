package dev.musicsense.orelha.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serve o Angular compilado na mesma origem da API (ver {@code spring.web.resources.static-locations} em
 * application.yml): toda rota que não é /api nem um arquivo estático cai no index.html, e o roteador do
 * Angular assume. Assim um único endereço (o do backend) basta para o túnel de hospedagem.
 * Sem o build do frontend no lugar, essas rotas respondem 404 como antes.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/tracks/**", "/artists/**", "/albums/**", "/compare", "/compare/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
