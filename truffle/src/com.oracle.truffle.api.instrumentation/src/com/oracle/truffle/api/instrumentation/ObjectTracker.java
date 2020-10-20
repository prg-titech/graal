package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import java.util.Arrays;

public class ObjectTracker {
    @CompilerDirectives.CompilationFinal(dimensions = 1) private volatile ObjectChangeListener[] listeners = null;

    public ObjectTracker() {}

    void addListener(ObjectChangeListener l) {
        CompilerAsserts.neverPartOfCompilation();
        synchronized (this) {
            if (listeners == null) {
                listeners = new ObjectChangeListener[]{l};
            } else {
                int i = listeners.length;
                ObjectChangeListener[] newListeners = Arrays.copyOf(listeners, i + 1);
                newListeners[i] = l;
                listeners = newListeners;
            }
        }
    }

    @ExplodeLoop
    public void notifyAssignment(Object object, Object key, Object value) {
        CompilerAsserts.partialEvaluationConstant(this);

        System.out.println("notifyAssignment: Object: " + object + " / Key: " + key + " Value: ");

        if (listeners != null) {
            ObjectChangeListener[] ls = listeners;
            FieldReassignedEvent e = new FieldReassignedEvent(object, key, value);
            for (ObjectChangeListener listener : ls) {
                listener.onFieldAssigned(e);
            }
        }
    }
}
