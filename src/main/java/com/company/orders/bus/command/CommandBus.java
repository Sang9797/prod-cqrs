package com.company.orders.bus.command;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * COMMAND BUS — dispatches write operations to registered handlers.
 *
 * <p>
 * Spring injects all CommandHandler beans as a List at startup. Each handler self-declares its
 * command type via commandType().
 *
 * <p>
 * Virtual threads: because Tomcat uses virtual threads (spring.threads.virtual.enabled=true), each
 * dispatch() call already runs on a separate virtual thread automatically.
 */
@Component
public class CommandBus {

    private static final Logger LOG = LoggerFactory.getLogger(CommandBus.class);
    private final Map<Class<?>, CommandHandler<?, ?>> registry = new HashMap<>();
    private final MeterRegistry meterRegistry;

    public CommandBus(List<CommandHandler<?, ?>> handlers, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        handlers.forEach(
                h -> {
                    registry.put(h.commandType(), h);
                    LOG.info(
                            "[CommandBus] registered {} → {}",
                            h.commandType().getSimpleName(),
                            h.getClass().getSimpleName());
                });
        LOG.info("[CommandBus] ready — {} handler(s)", registry.size());
    }

    @SuppressWarnings("unchecked")
    public <C extends Command<R>, R> R dispatch(C command) {
        var handler = (CommandHandler<C, R>) registry.get(command.getClass());
        if (handler == null) {
            throw new IllegalStateException(
                    "No handler registered for: " + command.getClass().getSimpleName());
        }
        LOG.debug("[CommandBus] dispatching {}", command.getClass().getSimpleName());
        return Timer.builder("cqrs.command.duration")
                .tag("command", command.getClass().getSimpleName())
                .description("Time to handle a command")
                .register(meterRegistry)
                .record(() -> handler.handle(command));
    }
}
