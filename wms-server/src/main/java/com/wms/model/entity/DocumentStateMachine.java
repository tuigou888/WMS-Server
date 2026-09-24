package com.wms.model.entity;

import com.wms.common.BusinessException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Shared persisted status vocabulary and legal transitions for WMS business documents. */
public final class DocumentStateMachine {
    public enum State { DRAFT, QC_HOLD, APPROVED, REJECTED, COMPLETED, CANCELLED }
    private static final Map<State, Set<State>> TRANSITIONS = new EnumMap<>(State.class);
    static {
        TRANSITIONS.put(State.DRAFT, EnumSet.of(State.QC_HOLD, State.APPROVED, State.REJECTED, State.CANCELLED));
        TRANSITIONS.put(State.QC_HOLD, EnumSet.of(State.DRAFT, State.APPROVED, State.CANCELLED));
        TRANSITIONS.put(State.APPROVED, EnumSet.of(State.COMPLETED, State.CANCELLED));
        TRANSITIONS.put(State.REJECTED, EnumSet.of(State.CANCELLED));
        TRANSITIONS.put(State.COMPLETED, EnumSet.of(State.APPROVED));
        TRANSITIONS.put(State.CANCELLED, EnumSet.noneOf(State.class));
    }
    private DocumentStateMachine() {}
    public static String transition(String currentValue, String nextValue) {
        State current = parse(currentValue), next = parse(nextValue);
        if (current == next) return next.name();
        if (!TRANSITIONS.get(current).contains(next)) throw new BusinessException("不允许的单据状态流转：" + current + " → " + next);
        return next.name();
    }
    private static State parse(String value) {
        try { return State.valueOf(value); }
        catch (RuntimeException e) { throw new BusinessException("未知单据状态：" + value); }
    }
}
