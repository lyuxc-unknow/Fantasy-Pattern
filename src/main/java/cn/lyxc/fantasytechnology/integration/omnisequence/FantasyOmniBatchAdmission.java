package cn.lyxc.fantasytechnology.integration.omnisequence;

import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.api.crafting.OmniBatchRequest;

import java.util.function.Function;

/// Single-use API admission kept outside the Mixin package so it remains a normal loadable class.
public final class FantasyOmniBatchAdmission implements OmniBatchAdmission {
    private final long maxCrafts;
    private final Function<OmniBatchRequest, OmniBatchDelivery.RejectReason> commit;
    private boolean used;

    public FantasyOmniBatchAdmission(long maxCrafts,
            Function<OmniBatchRequest, OmniBatchDelivery.RejectReason> commit) {
        this.maxCrafts = maxCrafts;
        this.commit = commit;
    }

    @Override
    public long maxCrafts() {
        return maxCrafts;
    }

    @Override
    public void commit(OmniBatchDelivery delivery) {
        if (used) {
            delivery.reject(OmniBatchDelivery.Rejection.reject(
                    OmniBatchDelivery.RejectReason.INTERNAL_ERROR));
            return;
        }
        used = true;

        OmniBatchDelivery.RejectReason rejection;
        try {
            rejection = commit.apply(delivery.request());
        } catch (RuntimeException exception) {
            rejection = OmniBatchDelivery.RejectReason.INTERNAL_ERROR;
        }
        if (rejection == null) {
            delivery.accept(new OmniBatchDelivery.Receipt(
                    OmniBatchDelivery.Ownership.PERSISTED_PROVIDER_QUEUE,
                    OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE));
        } else {
            delivery.reject(OmniBatchDelivery.Rejection.reject(rejection));
        }
    }
}
