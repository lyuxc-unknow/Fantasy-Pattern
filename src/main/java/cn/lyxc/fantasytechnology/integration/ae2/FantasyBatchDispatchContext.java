package cn.lyxc.fantasytechnology.integration.ae2;

import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

public final class FantasyBatchDispatchContext {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private FantasyBatchDispatchContext() {
    }

    public static Scope open(long craftCount, @Nullable KeyCounter reusableRemainders) {
        if (CURRENT.get() != null || craftCount < 2) {
            throw new IllegalStateException("Invalid nested fantasy batch dispatch");
        }
        CURRENT.set(new Context(craftCount, reusableRemainders));
        return new Scope();
    }

    @Nullable
    public static Context current() {
        return CURRENT.get();
    }

    public record Context(long craftCount, @Nullable KeyCounter reusableRemainders) {
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                CURRENT.remove();
            }
        }
    }
}
