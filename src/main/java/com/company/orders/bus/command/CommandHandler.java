package com.company.orders.bus.command;

/**
 * One handler per command type. commandType() is explicit — avoids reflection/CGLIB proxy issues.
 */
public interface CommandHandler<C extends Command<R>, R> {
  R handle(C command);

  Class<C> commandType();
}
