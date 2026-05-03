package com.company.orders.presentation.context;

import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;

public class ContextSnapshotFactory {

  public ContextSnapshot capture() {
    return new ContextSnapshot(SecurityContextHolder.getContext(), MDC.getCopyOfContextMap());
  }
}
