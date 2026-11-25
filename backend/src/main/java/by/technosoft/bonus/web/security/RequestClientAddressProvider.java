package by.technosoft.bonus.web.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import by.technosoft.bonus.service.audit.ClientAddressProvider;

/**
 * Реализация порта {@link ClientAddressProvider} для REST-слоя: адрес клиента текущего HTTP-запроса
 * (журнал аудита, SEC-4). Сервис аудита не знает о сервлетах (DIP, ISP).
 * Заголовок X-Forwarded-For не используется: без доверенного прокси его подделывает клиент.
 */
@Component
public class RequestClientAddressProvider implements ClientAddressProvider {

    @Override
    public Optional<InetAddress> currentAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        try {
            // Адрес — IP-литерал из сокета, поэтому обращения к DNS не происходит.
            return Optional.of(InetAddress.getByName(attributes.getRequest().getRemoteAddr()));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}
