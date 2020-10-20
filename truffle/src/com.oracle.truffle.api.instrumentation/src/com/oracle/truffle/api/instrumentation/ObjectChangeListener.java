package com.oracle.truffle.api.instrumentation;

public interface ObjectChangeListener {
    void onFieldAssigned(FieldReassignedEvent e);
}
